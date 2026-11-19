package com.orbitfiles.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.ArrayDeque

class OperationCancelledException : RuntimeException("Operação cancelada")

/** Cooperative cancellation flag shared between UI and worker threads. */
class CancelToken {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() { isCancelled = true }

    fun throwIfCancelled() { if (isCancelled) throw OperationCancelledException() }
}

data class Progress(
    val doneBytes: Long,
    val totalBytes: Long,
    val doneItems: Int,
    val totalItems: Int,
    val currentName: String,
) {
    val fraction: Float get() = when {
        totalBytes > 0 -> (doneBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
        totalItems > 0 -> (doneItems.toFloat() / totalItems).coerceIn(0f, 1f)
        else -> 0f
    }
}

typealias ProgressListener = (Progress) -> Unit

enum class ConflictPolicy { SKIP, OVERWRITE, KEEP_BOTH }

data class TransferResult(
    /** source -> created destination (top level items only). */
    val created: List<Pair<File, File>>,
    val skipped: List<File>,
    val failed: List<Pair<File, String>>,
) {
    val ok: Boolean get() = failed.isEmpty()
}

data class Totals(val bytes: Long, val files: Int, val folders: Int)

/**
 * Low-level, framework independent file engine. Every long running method checks the
 * [CancelToken] between buffers so cancellation is instant even for huge files.
 */
object FileOps {
    private const val BUFFER = 256 * 1024
    private val invalidChars = Regex("""[\\/:*?"<>|\u0000]""")

    fun isSymlink(f: File): Boolean = try { Files.isSymbolicLink(f.toPath()) } catch (_: Exception) { false }

    /** Returns null when valid, otherwise a human readable reason. */
    fun validateName(name: String): String? = when {
        name.isBlank() -> "O nome não pode ficar vazio"
        name == "." || name == ".." -> "Nome reservado"
        invalidChars.containsMatchIn(name) -> "Caracteres não permitidos: \\ / : * ? \" < > |"
        name.toByteArray().size > 255 -> "Nome longo demais"
        else -> null
    }

    /** "foto.jpg" -> "foto (1).jpg", "foto (2).jpg"... until it does not exist in [dir]. */
    fun uniqueName(dir: File, name: String): String {
        if (!File(dir, name).exists()) return name
        val ext = FileCategory.extensionOf(name)
        val isDirName = File(dir, name).isDirectory
        val base = if (ext.isEmpty() || isDirName) name else name.dropLast(ext.length + 1)
        val suffix = if (ext.isEmpty() || isDirName) "" else ".$ext"
        val cleanBase = base.replace(Regex(""" \(\d+\)$"""), "")
        var i = 1
        while (true) {
            val candidate = "$cleanBase ($i)$suffix"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
    }

    fun totals(sources: List<File>, token: CancelToken? = null): Totals {
        var bytes = 0L; var files = 0; var folders = 0
        val stack = ArrayDeque<File>(sources)
        while (stack.isNotEmpty()) {
            token?.throwIfCancelled()
            val f = stack.pop()
            if (f.isDirectory && !isSymlink(f)) {
                folders++
                f.listFiles()?.forEach { stack.push(it) }
            } else if (f.exists() || isSymlink(f)) {
                files++; bytes += f.length()
            }
        }
        return Totals(bytes, files, folders)
    }

    fun isInside(child: File, parent: File): Boolean {
        val c = child.canonicalFile.path
        val p = parent.canonicalFile.path
        return c == p || c.startsWith(p + File.separator)
    }

    fun createFolder(parent: File, name: String): File {
        validateName(name)?.let { throw IOException(it) }
        val f = File(parent, name)
        if (f.exists()) throw IOException("Já existe um item chamado \"$name\"")
        if (!f.mkdirs()) throw IOException("Não foi possível criar a pasta")
        return f
    }

    fun createFile(parent: File, name: String): File {
        validateName(name)?.let { throw IOException(it) }
        val f = File(parent, name)
        if (f.exists()) throw IOException("Já existe um item chamado \"$name\"")
        if (!f.createNewFile()) throw IOException("Não foi possível criar o arquivo")
        return f
    }

    fun rename(file: File, newName: String): File {
        validateName(newName)?.let { throw IOException(it) }
        val target = File(file.parentFile, newName)
        if (target.path == file.path) return file
        val caseOnly = target.path.equals(file.path, ignoreCase = true)
        if (target.exists() && !caseOnly) throw IOException("Já existe um item chamado \"$newName\"")
        if (caseOnly) {
            // Two step rename for case-insensitive file systems (e.g. FAT SD cards)
            val tmp = File(file.parentFile, ".orbit_tmp_${System.nanoTime()}")
            if (!file.renameTo(tmp) || !tmp.renameTo(target)) throw IOException("Falha ao renomear")
        } else if (!file.renameTo(target)) {
            throw IOException("Falha ao renomear \"${file.name}\"")
        }
        return target
    }

    fun copy(
        sources: List<File>,
        destDir: File,
        policy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
        token: CancelToken = CancelToken(),
        onProgress: ProgressListener = {},
    ): TransferResult = transfer(sources, destDir, policy, token, onProgress, move = false)

    fun move(
        sources: List<File>,
        destDir: File,
        policy: ConflictPolicy = ConflictPolicy.KEEP_BOTH,
        token: CancelToken = CancelToken(),
        onProgress: ProgressListener = {},
    ): TransferResult = transfer(sources, destDir, policy, token, onProgress, move = true)

    private fun transfer(
        sources: List<File>,
        destDir: File,
        policy: ConflictPolicy,
        token: CancelToken,
        onProgress: ProgressListener,
        move: Boolean,
    ): TransferResult {
        if (!destDir.isDirectory && !destDir.mkdirs()) throw IOException("Destino inválido: ${destDir.path}")
        val created = mutableListOf<Pair<File, File>>()
        val skipped = mutableListOf<File>()
        val failed = mutableListOf<Pair<File, String>>()
        val totals = totals(sources, token)
        val state = ProgressState(totals.bytes, totals.files + totals.folders, onProgress)

        for (src in sources) {
            token.throwIfCancelled()
            try {
                if (!src.exists() && !isSymlink(src)) { failed += src to "Não encontrado"; continue }
                if (src.isDirectory && isInside(destDir, src)) {
                    failed += src to "Não é possível ${if (move) "mover" else "copiar"} uma pasta para dentro dela mesma"
                    continue
                }
                var target = File(destDir, src.name)
                if (target.canonicalPath == src.canonicalPath) {
                    if (move) { skipped += src; continue }
                    // Duplicating in place always keeps both copies.
                    target = File(destDir, uniqueName(destDir, src.name))
                }
                if (target.exists()) {
                    when (policy) {
                        ConflictPolicy.SKIP -> { skipped += src; state.skip(totals(listOf(src))); continue }
                        ConflictPolicy.KEEP_BOTH -> target = File(destDir, uniqueName(destDir, src.name))
                        ConflictPolicy.OVERWRITE -> if (target.canonicalPath != src.canonicalPath) deleteRecursively(target, token)
                    }
                }
                if (move && src.renameTo(target)) {
                    // Fast path: same volume, O(1) rename.
                    state.skip(totals(listOf(target)))
                } else {
                    copyRecursive(src, target, token, state)
                    if (move) deleteRecursively(src, token)
                }
                created += src to target
            } catch (e: OperationCancelledException) {
                throw e
            } catch (e: Exception) {
                failed += src to (e.message ?: e.javaClass.simpleName)
            }
        }
        state.finish()
        return TransferResult(created, skipped, failed)
    }

    private class ProgressState(val totalBytes: Long, val totalItems: Int, val listener: ProgressListener) {
        var bytes = 0L
        var items = 0
        var current = ""
        private var lastEmit = 0L

        fun emit(force: Boolean = false) {
            val now = System.nanoTime()
            if (force || now - lastEmit > 80_000_000L) { // ~12 updates/s keeps UI smooth and cheap
                lastEmit = now
                listener(Progress(bytes, totalBytes, items, totalItems, current))
            }
        }

        fun skip(t: Totals) { bytes += t.bytes; items += t.files + t.folders; emit() }
        fun finish() { emit(force = true) }
    }

    private fun copyRecursive(src: File, dst: File, token: CancelToken, state: ProgressState) {
        token.throwIfCancelled()
        state.current = src.name
        if (src.isDirectory && !isSymlink(src)) {
            if (!dst.exists() && !dst.mkdirs()) throw IOException("Não foi possível criar ${dst.name}")
            state.items++
            state.emit()
            src.listFiles()?.forEach { copyRecursive(it, File(dst, it.name), token, state) }
            dst.setLastModified(src.lastModified())
        } else {
            copyFile(src, dst, token) { delta -> state.bytes += delta; state.emit() }
            state.items++
            state.emit()
        }
    }

    fun copyFile(src: File, dst: File, token: CancelToken = CancelToken(), onBytes: (Long) -> Unit = {}) {
        dst.parentFile?.mkdirs()
        val partial = File(dst.parentFile, ".${dst.name}.orbitpart")
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(partial).use { output ->
                    val buf = ByteArray(BUFFER)
                    while (true) {
                        token.throwIfCancelled()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        onBytes(n.toLong())
                    }
                    output.fd.sync()
                }
            }
            if (dst.exists()) dst.delete()
            if (!partial.renameTo(dst)) throw IOException("Falha ao finalizar ${dst.name}")
            dst.setLastModified(src.lastModified())
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }
    }

    /** Deletes files and folders depth first. Symlinks are removed, never followed. */
    fun deleteRecursively(target: File, token: CancelToken = CancelToken(), onItem: (File) -> Unit = {}): Boolean {
        if (!target.exists() && !isSymlink(target)) return true
        val stack = ArrayDeque<File>()
        val post = ArrayDeque<File>()
        stack.push(target)
        while (stack.isNotEmpty()) {
            token.throwIfCancelled()
            val f = stack.pop()
            post.push(f)
            if (f.isDirectory && !Files.isSymbolicLink(f.toPath())) f.listFiles()?.forEach { stack.push(it) }
        }
        var ok = true
        while (post.isNotEmpty()) {
            val f = post.pop()
            val deleted = try { Files.deleteIfExists(f.toPath()); true } catch (_: Exception) { f.delete() }
            if (!deleted) ok = false else onItem(f)
        }
        return ok && !Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)
    }

    /** Count of direct children (cheap, used for folder subtitles). */
    fun childCount(dir: File, includeHidden: Boolean): Int =
        dir.list()?.count { includeHidden || !it.startsWith(".") } ?: 0
}
