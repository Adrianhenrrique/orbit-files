package com.orbitfiles.app.data

import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.FileOps
import com.orbitfiles.core.FileSorter
import com.orbitfiles.core.SortBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FileEntry(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val hidden: Boolean,
    val category: FileCategory,
    val childCount: Int?,
) {
    val path: String get() = file.absolutePath

    companion object {
        fun of(f: File, countChildren: Boolean = true, includeHidden: Boolean = false): FileEntry {
            val dir = f.isDirectory
            return FileEntry(
                file = f,
                name = f.name,
                isDirectory = dir,
                size = if (dir) 0L else f.length(),
                modified = f.lastModified(),
                hidden = f.name.startsWith("."),
                category = if (dir) FileCategory.FOLDER else FileCategory.ofName(f.name),
                childCount = if (dir && countChildren) FileOps.childCount(f, includeHidden) else null,
            )
        }
    }
}

data class StorageVolumeInfo(
    val name: String,
    val root: File,
    val total: Long,
    val free: Long,
    val removable: Boolean,
    val primary: Boolean,
) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val usedFraction: Float get() = if (total <= 0) 0f else used.toFloat() / total
}

/** Smart collections shown on the home screen, backed by the MediaStore index (instant, no scan). */
enum class SmartCollection(val label: String, val category: FileCategory) {
    IMAGES("Imagens", FileCategory.IMAGE),
    VIDEOS("Vídeos", FileCategory.VIDEO),
    AUDIO("Áudio", FileCategory.AUDIO),
    DOCUMENTS("Documentos", FileCategory.DOCUMENT),
    ARCHIVES("Compactados", FileCategory.ARCHIVE),
    APKS("APKs", FileCategory.APK),
    DOWNLOADS("Downloads", FileCategory.FOLDER),
}

class StorageRepository(private val context: Context) {

    val primaryRoot: File get() = Environment.getExternalStorageDirectory()

    fun hasAllFilesAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @Volatile private var cachedVolumes: List<StorageVolumeInfo> = emptyList()
    @Volatile private var cachedAt = 0L

    /** Mounted volumes (cached for a few seconds: called often from UI code). */
    fun volumes(): List<StorageVolumeInfo> {
        val now = System.currentTimeMillis()
        if (cachedVolumes.isNotEmpty() && now - cachedAt < 4000) return cachedVolumes
        return queryVolumes().also { cachedVolumes = it; cachedAt = now }
    }

    private fun queryVolumes(): List<StorageVolumeInfo> {
        val result = mutableListOf<StorageVolumeInfo>()
        val sm = context.getSystemService(StorageManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && sm != null) {
            for (v in sm.storageVolumes) {
                val dir = v.directory ?: continue
                if (!dir.canRead()) continue
                result += info(v.getDescription(context), dir, v.isRemovable, v.isPrimary)
            }
        } else {
            result += info("Armazenamento interno", primaryRoot, removable = false, primary = true)
            context.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { appDir ->
                val root = File(appDir.path.substringBefore("/Android/data"))
                if (root.canRead()) result += info("Cartão SD", root, removable = true, primary = false)
            }
        }
        if (result.isEmpty()) result += info("Armazenamento interno", primaryRoot, removable = false, primary = true)
        return result.map { if (it.primary) it.copy(name = "Armazenamento interno") else it }
    }

    private fun info(name: String, root: File, removable: Boolean, primary: Boolean): StorageVolumeInfo {
        val (total, free) = runCatching { StatFs(root.path).let { it.totalBytes to it.availableBytes } }.getOrDefault(0L to 0L)
        return StorageVolumeInfo(name, root, total, free, removable, primary)
    }

    fun volumeFor(file: File): StorageVolumeInfo? =
        volumes().filter { file.absolutePath == it.root.path || file.absolutePath.startsWith(it.root.path + "/") }
            .maxByOrNull { it.root.path.length }

    suspend fun list(
        dir: File,
        showHidden: Boolean,
        sortBy: SortBy,
        ascending: Boolean,
        foldersFirst: Boolean,
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        val children = dir.listFiles() ?: throw SecurityException("Sem permissão para ler esta pasta")
        children.asSequence()
            .filter { showHidden || !it.name.startsWith(".") }
            .map { FileEntry.of(it, countChildren = true, includeHidden = showHidden) }
            .toList()
            .sortedWith(FileSorter.comparator(sortBy, ascending, foldersFirst, { it.isDirectory }, { it.name }, { it.size }, { it.modified }))
    }

    // ---------- MediaStore powered collections ----------

    private val filesUri get() = MediaStore.Files.getContentUri("external")

    private fun selectionFor(c: SmartCollection): Pair<String, Array<String>> {
        val type = MediaStore.Files.FileColumns.MEDIA_TYPE
        @Suppress("DEPRECATION") val data = MediaStore.Files.FileColumns.DATA
        fun exts(cat: FileCategory): Pair<String, Array<String>> {
            val list = FileCategory.extensionsOf(cat).toList()
            return list.joinToString(" OR ") { "$data LIKE ?" }.let { "($it)" } to list.map { "%.$it" }.toTypedArray()
        }
        return when (c) {
            SmartCollection.IMAGES -> "$type = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            SmartCollection.VIDEOS -> "$type = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            SmartCollection.AUDIO -> "$type = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
            SmartCollection.DOCUMENTS -> {
                val cats = listOf(FileCategory.DOCUMENT, FileCategory.PDF, FileCategory.SPREADSHEET, FileCategory.PRESENTATION, FileCategory.EBOOK)
                val list = cats.flatMap { FileCategory.extensionsOf(it) } + listOf("txt", "md")
                "(" + list.joinToString(" OR ") { "$data LIKE ?" } + ")" to list.map { "%.$it" }.toTypedArray()
            }
            SmartCollection.ARCHIVES -> exts(FileCategory.ARCHIVE)
            SmartCollection.APKS -> exts(FileCategory.APK)
            SmartCollection.DOWNLOADS -> "$data LIKE ?" to arrayOf(File(primaryRoot, Environment.DIRECTORY_DOWNLOADS).path + "/%")
        }
    }

    suspend fun collection(c: SmartCollection, limit: Int = 5000): List<FileEntry> = withContext(Dispatchers.IO) {
        val (sel, args) = selectionFor(c)
        query("$sel AND ${MediaStore.Files.FileColumns.SIZE} > 0", args, limit)
    }

    suspend fun collectionCounts(): Map<SmartCollection, Pair<Int, Long>> = withContext(Dispatchers.IO) {
        SmartCollection.entries.associateWith { c ->
            val (sel, args) = selectionFor(c)
            runCatching {
                context.contentResolver.query(filesUri, arrayOf(MediaStore.Files.FileColumns.SIZE), "$sel AND ${MediaStore.Files.FileColumns.SIZE} > 0", args, null)?.use { cur ->
                    var total = 0L
                    while (cur.moveToNext()) total += cur.getLong(0)
                    cur.count to total
                } ?: (0 to 0L)
            }.getOrDefault(0 to 0L)
        }
    }

    suspend fun recent(limit: Int = 30): List<FileEntry> = withContext(Dispatchers.IO) {
        val since = (System.currentTimeMillis() / 1000) - 30L * 24 * 3600
        query(
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} > ? AND ${MediaStore.Files.FileColumns.SIZE} > 0 AND ${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL",
            arrayOf(since.toString()), limit,
        ).filter { !it.hidden && !it.path.contains("/.") }
    }

    private fun query(selection: String, args: Array<String>, limit: Int): List<FileEntry> {
        @Suppress("DEPRECATION") val dataCol = MediaStore.Files.FileColumns.DATA
        val projection = arrayOf(dataCol, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED)
        val out = ArrayList<FileEntry>()
        val cursor: Cursor? = runCatching {
            context.contentResolver.query(filesUri, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")
        }.getOrNull()
        cursor?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val path = c.getString(0) ?: continue
                val f = File(path)
                if (!f.exists()) continue
                out += FileEntry(
                    file = f, name = f.name, isDirectory = false, size = c.getLong(1),
                    modified = c.getLong(2) * 1000, hidden = f.name.startsWith("."),
                    category = FileCategory.ofName(f.name), childCount = null,
                )
            }
        }
        return out
    }

    /** Standard folders for quick navigation. */
    fun quickFolders(): List<Pair<String, File>> = listOf(
        "Downloads" to Environment.DIRECTORY_DOWNLOADS,
        "Câmera" to Environment.DIRECTORY_DCIM,
        "Imagens" to Environment.DIRECTORY_PICTURES,
        "Documentos" to Environment.DIRECTORY_DOCUMENTS,
        "Músicas" to Environment.DIRECTORY_MUSIC,
        "Filmes" to Environment.DIRECTORY_MOVIES,
    ).map { (label, dir) -> label to File(primaryRoot, dir) }.filter { it.second.exists() }

    fun displayPath(file: File): String {
        val vol = volumeFor(file) ?: return file.path
        val rel = file.path.removePrefix(vol.root.path).trimStart('/')
        return if (rel.isEmpty()) vol.name else "${vol.name}/$rel"
    }
}
