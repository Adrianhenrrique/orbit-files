package com.orbitfiles.core

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.PriorityQueue

object Checksums {
    fun hash(file: File, algorithm: String = "SHA-256", token: CancelToken = CancelToken(), limitBytes: Long = Long.MAX_VALUE): String {
        val md = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { input ->
            val buf = ByteArray(256 * 1024)
            var remaining = limitBytes
            while (remaining > 0) {
                token.throwIfCancelled()
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) break
                md.update(buf, 0, n)
                remaining -= n
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Folder node in the storage map. Only directories are kept as nodes; files are aggregated. */
class DirNode(val file: File, val parent: DirNode?) {
    var size: Long = 0
    var fileCount: Int = 0
    val children = mutableListOf<DirNode>()
    val name: String get() = file.name.ifEmpty { file.path }
}

data class StorageReport(
    val root: DirNode,
    val byCategory: Map<FileCategory, Long>,
    val countByCategory: Map<FileCategory, Int>,
    val largestFiles: List<File>,
    val totalFiles: Int,
    val totalFolders: Int,
    val scanMillis: Long,
)

object StorageAnalyzer {
    /** Buckets used in the ring chart (documents of all kinds are merged). */
    fun bucket(cat: FileCategory): FileCategory = when (cat) {
        FileCategory.PDF, FileCategory.SPREADSHEET, FileCategory.PRESENTATION, FileCategory.TEXT, FileCategory.EBOOK, FileCategory.DOCUMENT -> FileCategory.DOCUMENT
        FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO, FileCategory.ARCHIVE, FileCategory.APK -> cat
        else -> FileCategory.OTHER
    }

    fun scan(
        rootDir: File,
        topFiles: Int = 60,
        token: CancelToken = CancelToken(),
        onProgress: (scannedFiles: Int, current: String) -> Unit = { _, _ -> },
    ): StorageReport {
        val start = System.currentTimeMillis()
        val root = DirNode(rootDir, null)
        val bySize = HashMap<FileCategory, Long>()
        val byCount = HashMap<FileCategory, Int>()
        val largest = PriorityQueue<File>(topFiles + 1, compareBy { it.length() })
        var files = 0; var folders = 0
        val stack = ArrayDeque<DirNode>()
        stack.push(root)
        val order = ArrayList<DirNode>()
        var lastEmit = 0L
        while (stack.isNotEmpty()) {
            token.throwIfCancelled()
            val node = stack.pop()
            order += node
            val list = node.file.listFiles() ?: continue
            for (f in list) {
                if (f.isDirectory) {
                    if (FileOps.isSymlink(f)) continue
                    folders++
                    val child = DirNode(f, node)
                    node.children += child
                    stack.push(child)
                } else {
                    val len = f.length()
                    files++
                    node.size += len
                    node.fileCount++
                    val b = bucket(FileCategory.ofName(f.name))
                    bySize[b] = (bySize[b] ?: 0L) + len
                    byCount[b] = (byCount[b] ?: 0) + 1
                    largest.add(f)
                    if (largest.size > topFiles) largest.poll()
                }
            }
            val now = System.nanoTime()
            if (now - lastEmit > 150_000_000L) { lastEmit = now; onProgress(files, node.file.path) }
        }
        // Bottom-up aggregation (reverse pre-order guarantees children before parents).
        for (i in order.indices.reversed()) {
            val n = order[i]
            n.children.sortByDescending { it.size }
            n.parent?.let { p -> p.size += n.size; p.fileCount += n.fileCount }
        }
        return StorageReport(
            root, bySize, byCount,
            largest.toList().sortedByDescending { it.length() },
            files, folders, System.currentTimeMillis() - start,
        )
    }
}

data class DuplicateGroup(val files: List<File>, val size: Long, val hash: String) {
    val wasted: Long get() = size * (files.size - 1)
}

/**
 * Three stage duplicate finder: group by size -> hash of first 64 KB -> full SHA-256.
 * Avoids reading most files entirely, so it stays fast on phones with tens of GB of media.
 */
object DuplicateFinder {
    fun find(
        roots: List<File>,
        minSize: Long = 1024,
        includeHidden: Boolean = false,
        token: CancelToken = CancelToken(),
        onProgress: (stage: String, done: Int, total: Int) -> Unit = { _, _, _ -> },
    ): List<DuplicateGroup> {
        val bySize = HashMap<Long, MutableList<File>>()
        val stack = ArrayDeque<File>(roots)
        var seen = 0
        while (stack.isNotEmpty()) {
            token.throwIfCancelled()
            val dir = stack.pop()
            dir.listFiles()?.forEach { f ->
                if (!includeHidden && f.name.startsWith(".")) return@forEach
                if (f.isDirectory) { if (!FileOps.isSymlink(f)) stack.push(f) }
                else {
                    val len = f.length()
                    if (len >= minSize) bySize.getOrPut(len) { mutableListOf() }.add(f)
                    seen++
                    if (seen % 500 == 0) onProgress("Indexando", seen, 0)
                }
            }
        }
        val candidates = bySize.values.filter { it.size > 1 }
        val total = candidates.sumOf { it.size }
        var done = 0
        val groups = mutableListOf<DuplicateGroup>()
        for (sameSize in candidates) {
            val byHead = sameSize.groupBy { f ->
                token.throwIfCancelled()
                done++; onProgress("Comparando", done, total)
                runCatching { Checksums.hash(f, "SHA-1", token, 64 * 1024) }.getOrNull()
            }.filterKeys { it != null }.values.filter { it.size > 1 }
            for (headGroup in byHead) {
                val size = headGroup.first().length()
                val full = if (size <= 64 * 1024) mapOf("h" to headGroup) else headGroup.groupBy { f ->
                    runCatching { Checksums.hash(f, "SHA-256", token) }.getOrNull()
                }.filterKeys { it != null }.mapKeys { it.key!! }
                full.forEach { (h, list) -> if (list.size > 1) groups += DuplicateGroup(list.sortedBy { it.lastModified() }, size, h) }
            }
        }
        return groups.sortedByDescending { it.wasted }
    }
}

enum class JunkKind(val label: String, val description: String) {
    TEMP("Arquivos temporários", "Restos de downloads e arquivos .tmp/.log"),
    THUMBNAILS("Cache de miniaturas", "Miniaturas geradas pelo sistema; são recriadas quando necessário"),
    EMPTY_FOLDERS("Pastas vazias", "Pastas sem nenhum conteúdo"),
    APKS("Instaladores APK", "Arquivos de instalação que normalmente não são mais necessários"),
    LARGE("Arquivos grandes", "Arquivos acima de 100 MB"),
    OLD_DOWNLOADS("Downloads antigos", "Itens na pasta Download há mais de 90 dias"),
}

data class JunkItem(val file: File, val kind: JunkKind, val size: Long)

object CleanerScanner {
    private val tempExt = setOf("tmp", "temp", "log", "bak", "old", "crdownload", "part", "partial", "orbitpart")
    private val tempNames = setOf(".ds_store", "thumbs.db", "desktop.ini")

    fun scan(
        root: File,
        now: Long = System.currentTimeMillis(),
        largeThreshold: Long = 100L * 1024 * 1024,
        oldDownloadDays: Int = 90,
        token: CancelToken = CancelToken(),
        onProgress: (Int) -> Unit = {},
    ): Map<JunkKind, List<JunkItem>> {
        val result = JunkKind.entries.associateWith { mutableListOf<JunkItem>() }
        val downloadsPrefix = File(root, "Download").path + File.separator
        val oldLimit = now - oldDownloadDays * TrashEntry.DAY
        val stack = ArrayDeque<File>()
        stack.push(root)
        var count = 0
        val dirs = ArrayList<File>()
        while (stack.isNotEmpty()) {
            token.throwIfCancelled()
            val dir = stack.pop()
            dirs += dir
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory) {
                    if (FileOps.isSymlink(f) || f.name == ".OrbitTrash" || f.name == ".OrbitVault") continue
                    if (f.name == ".thumbnails" || f.name == ".Thumbnails") {
                        val t = FileOps.totals(listOf(f), token)
                        if (t.bytes > 0) result.getValue(JunkKind.THUMBNAILS) += JunkItem(f, JunkKind.THUMBNAILS, t.bytes)
                        continue
                    }
                    if (f.name == "Android" && dir == root) continue // app data is off-limits & not ours
                    stack.push(f)
                } else {
                    count++
                    if (count % 400 == 0) onProgress(count)
                    val len = f.length()
                    val ext = FileCategory.extensionOf(f.name)
                    when {
                        ext in tempExt || f.name.lowercase() in tempNames || f.name.startsWith("~$") ->
                            result.getValue(JunkKind.TEMP) += JunkItem(f, JunkKind.TEMP, len)
                        FileCategory.ofName(f.name) == FileCategory.APK ->
                            result.getValue(JunkKind.APKS) += JunkItem(f, JunkKind.APKS, len)
                    }
                    if (len >= largeThreshold) result.getValue(JunkKind.LARGE) += JunkItem(f, JunkKind.LARGE, len)
                    if (f.path.startsWith(downloadsPrefix) && f.lastModified() < oldLimit) {
                        result.getValue(JunkKind.OLD_DOWNLOADS) += JunkItem(f, JunkKind.OLD_DOWNLOADS, len)
                    }
                }
            }
        }
        // Empty folders: deepest first so nested empty chains are detected.
        val empty = HashSet<String>()
        for (d in dirs.sortedByDescending { it.path.length }) {
            if (d == root) continue
            val kids = d.listFiles() ?: continue
            if (kids.all { it.isDirectory && it.path in empty }) {
                empty += d.path
            }
        }
        // Report only top-most empty folders.
        empty.filter { File(it).parentFile?.path !in empty }.forEach {
            result.getValue(JunkKind.EMPTY_FOLDERS) += JunkItem(File(it), JunkKind.EMPTY_FOLDERS, 0)
        }
        return result.mapValues { (_, v) -> v.sortedByDescending { it.size } }
    }
}
