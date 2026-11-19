package com.orbitfiles.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ArchiveEntry(val path: String, val size: Long, val compressedSize: Long, val isDirectory: Boolean, val time: Long) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/')
}

object ArchiveManager {
    private const val BUFFER = 128 * 1024

    fun isSupported(file: File): Boolean = FileCategory.extensionOf(file.name) in setOf("zip", "jar", "apk", "cbz", "epub", "xapk", "apks")

    fun list(zip: File): List<ArchiveEntry> = ZipFile(zip).use { zf ->
        zf.entries().asSequence().map {
            ArchiveEntry(it.name, it.size.coerceAtLeast(0), it.compressedSize.coerceAtLeast(0), it.isDirectory, it.time)
        }.toList()
    }

    /** Compresses [sources] into [dest]. Folders keep their relative structure. */
    fun zip(
        sources: List<File>,
        dest: File,
        level: Int = 6,
        token: CancelToken = CancelToken(),
        onProgress: ProgressListener = {},
    ): File {
        val totals = FileOps.totals(sources, token)
        var done = 0L; var items = 0
        val partial = File(dest.parentFile, ".${dest.name}.orbitpart")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(partial), BUFFER)).use { zos ->
                zos.setLevel(level)
                val buf = ByteArray(BUFFER)
                fun add(f: File, path: String) {
                    token.throwIfCancelled()
                    if (f.isDirectory && !FileOps.isSymlink(f)) {
                        zos.putNextEntry(ZipEntry("$path/").apply { time = f.lastModified() })
                        zos.closeEntry()
                        f.listFiles()?.sortedBy { it.name }?.forEach { add(it, "$path/${it.name}") }
                    } else {
                        zos.putNextEntry(ZipEntry(path).apply { time = f.lastModified() })
                        FileInputStream(f).use { input ->
                            while (true) {
                                token.throwIfCancelled()
                                val n = input.read(buf)
                                if (n < 0) break
                                zos.write(buf, 0, n)
                                done += n
                                onProgress(Progress(done, totals.bytes, items, totals.files, f.name))
                            }
                        }
                        zos.closeEntry()
                        items++
                    }
                }
                sources.forEach { add(it, it.name) }
            }
            if (dest.exists()) dest.delete()
            if (!partial.renameTo(dest)) throw IOException("Falha ao salvar ${dest.name}")
            return dest
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }
    }

    /**
     * Extracts all (or only [only]) entries into [destDir]. Protects against "zip slip"
     * path traversal: any entry resolving outside [destDir] is rejected.
     */
    fun extract(
        zip: File,
        destDir: File,
        only: Set<String>? = null,
        token: CancelToken = CancelToken(),
        onProgress: ProgressListener = {},
    ): List<File> {
        destDir.mkdirs()
        val root = destDir.canonicalFile
        val out = mutableListOf<File>()
        ZipFile(zip).use { zf ->
            val entries = zf.entries().asSequence()
                .filter { e -> only == null || only.any { sel -> e.name == sel || e.name.startsWith(sel.trimEnd('/') + "/") } }
                .toList()
            val total = entries.sumOf { it.size.coerceAtLeast(0) }
            var done = 0L
            entries.forEachIndexed { i, e ->
                token.throwIfCancelled()
                val target = File(root, e.name).canonicalFile
                if (target.path != root.path && !target.path.startsWith(root.path + File.separator)) {
                    throw IOException("Entrada insegura bloqueada: ${e.name}")
                }
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zf.getInputStream(e).use { input -> write(input, target, token) { n -> done += n; onProgress(Progress(done, total, i, entries.size, e.name)) } }
                    if (e.time > 0) target.setLastModified(e.time)
                }
                out += target
            }
        }
        return out
    }

    /** Extracts a single entry to [dest] (used for previews from inside archives). */
    fun extractEntry(zip: File, entryPath: String, dest: File): File {
        ZipFile(zip).use { zf ->
            val e = zf.getEntry(entryPath) ?: throw IOException("Entrada não encontrada")
            dest.parentFile?.mkdirs()
            zf.getInputStream(e).use { write(it, dest, CancelToken()) {} }
        }
        return dest
    }

    private fun write(input: InputStream, target: File, token: CancelToken, onBytes: (Long) -> Unit) {
        BufferedInputStream(input).use { bin ->
            FileOutputStream(target).use { fos ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    token.throwIfCancelled()
                    val n = bin.read(buf)
                    if (n < 0) break
                    fos.write(buf, 0, n)
                    onBytes(n.toLong())
                }
            }
        }
    }

    /** Suggests a folder name for extraction: "fotos.zip" -> "fotos". */
    fun suggestedFolder(zip: File): String = zip.name.substringBeforeLast('.').ifBlank { "extraido" }
}
