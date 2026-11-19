package com.orbitfiles.core

import java.io.File
import java.io.IOException
import java.util.UUID

data class TrashEntry(
    val id: String,
    val originalPath: String,
    val deletedAt: Long,
    val isDirectory: Boolean,
    val size: Long,
) {
    val name: String get() = originalPath.substringAfterLast('/')

    fun daysLeft(retentionDays: Int, now: Long = System.currentTimeMillis()): Int {
        val expires = deletedAt + retentionDays * DAY
        return ((expires - now + DAY - 1) / DAY).toInt().coerceAtLeast(0)
    }

    companion object { const val DAY = 24L * 60 * 60 * 1000 }
}

/**
 * Recycle bin backed by a hidden folder. Items are moved (renamed when possible, so it is
 * instant) into `<root>/files/<id>` and described by a tab separated `index.tsv`.
 */
class TrashStore(val root: File) {
    private val filesDir = File(root, "files")
    private val index = File(root, "index.tsv")
    private val lock = Any()

    init {
        filesDir.mkdirs()
        File(root, ".nomedia").let { if (!it.exists()) runCatching { it.createNewFile() } }
    }

    fun list(): List<TrashEntry> = synchronized(lock) { read() }.sortedByDescending { it.deletedAt }

    fun totalSize(): Long = list().sumOf { it.size }

    fun fileFor(entry: TrashEntry): File = File(filesDir, entry.id)

    fun trash(file: File, token: CancelToken = CancelToken(), now: Long = System.currentTimeMillis()): TrashEntry {
        if (!file.exists()) throw IOException("\"${file.name}\" não existe mais")
        if (FileOps.isInside(root, file)) throw IOException("Não é possível mover a lixeira para ela mesma")
        val id = UUID.randomUUID().toString()
        val size = FileOps.totals(listOf(file), token).bytes
        val dest = File(filesDir, id)
        if (!file.renameTo(dest)) {
            // Different volume: copy then delete the original.
            FileOps.copy(listOf(file), filesDir, ConflictPolicy.OVERWRITE, token).let { res ->
                if (!res.ok) throw IOException(res.failed.first().second)
                File(filesDir, file.name).renameTo(dest)
            }
            FileOps.deleteRecursively(file, token)
        }
        val entry = TrashEntry(id, file.absolutePath, now, dest.isDirectory, size)
        synchronized(lock) { write(read() + entry) }
        return entry
    }

    /** Restores to the original location (or a unique sibling name if the path is taken). */
    fun restore(id: String): File = synchronized(lock) {
        val entries = read()
        val entry = entries.firstOrNull { it.id == id } ?: throw IOException("Item não encontrado na lixeira")
        val stored = fileFor(entry)
        val original = File(entry.originalPath)
        val parent = original.parentFile ?: throw IOException("Local original inválido")
        parent.mkdirs()
        val target = File(parent, FileOps.uniqueName(parent, original.name))
        if (!stored.renameTo(target)) {
            val res = FileOps.copy(listOf(stored), parent, ConflictPolicy.KEEP_BOTH)
            if (!res.ok) throw IOException(res.failed.first().second)
            res.created.firstOrNull()?.second?.renameTo(target)
            FileOps.deleteRecursively(stored)
        }
        write(entries.filterNot { it.id == id })
        target
    }

    fun deleteForever(id: String): Boolean = synchronized(lock) {
        val entries = read()
        val entry = entries.firstOrNull { it.id == id } ?: return false
        FileOps.deleteRecursively(fileFor(entry))
        write(entries.filterNot { it.id == id })
        true
    }

    /** Removes entries older than [retentionDays]. Returns number of purged items. */
    fun purge(retentionDays: Int, now: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val limit = now - retentionDays * TrashEntry.DAY
        val (old, keep) = read().partition { it.deletedAt < limit }
        old.forEach { FileOps.deleteRecursively(fileFor(it)) }
        write(keep)
        old.size
    }

    fun empty(): Int = synchronized(lock) {
        val all = read()
        all.forEach { FileOps.deleteRecursively(fileFor(it)) }
        filesDir.listFiles()?.forEach { FileOps.deleteRecursively(it) } // orphans
        write(emptyList())
        all.size
    }

    private fun read(): List<TrashEntry> {
        if (!index.exists()) return emptyList()
        return index.readLines().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5) null else runCatching {
                TrashEntry(p[0], unescape(p[1]), p[2].toLong(), p[3] == "1", p[4].toLong())
            }.getOrNull()
        }.filter { fileFor(it).exists() }
    }

    private fun write(entries: List<TrashEntry>) {
        val tmp = File(root, "index.tsv.tmp")
        tmp.writeText(entries.joinToString("\n") {
            listOf(it.id, escape(it.originalPath), it.deletedAt, if (it.isDirectory) "1" else "0", it.size).joinToString("\t")
        })
        if (!tmp.renameTo(index)) { index.delete(); tmp.renameTo(index) }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 't' -> sb.append('\t'); 'n' -> sb.append('\n'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}
