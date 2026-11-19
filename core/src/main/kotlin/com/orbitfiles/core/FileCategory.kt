package com.orbitfiles.core

import java.io.File
import java.util.Locale

/** High level grouping used for icons, colors, filters and storage statistics. */
enum class FileCategory(val label: String) {
    FOLDER("Pastas"),
    IMAGE("Imagens"),
    VIDEO("Vídeos"),
    AUDIO("Áudio"),
    DOCUMENT("Documentos"),
    PDF("PDF"),
    SPREADSHEET("Planilhas"),
    PRESENTATION("Apresentações"),
    ARCHIVE("Compactados"),
    APK("Apps (APK)"),
    CODE("Código"),
    TEXT("Texto"),
    EBOOK("E-books"),
    FONT("Fontes"),
    OTHER("Outros");

    companion object {
        private val byExt: Map<String, FileCategory> = buildMap {
            fun put(cat: FileCategory, vararg exts: String) = exts.forEach { put(it, cat) }
            put(IMAGE, "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "ico", "tif", "tiff", "raw", "dng", "cr2", "nef", "arw")
            put(VIDEO, "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "wmv", "ts", "mpeg", "mpg")
            put(AUDIO, "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "amr", "mid", "midi", "aiff")
            put(PDF, "pdf")
            put(DOCUMENT, "doc", "docx", "odt", "rtf", "pages")
            put(SPREADSHEET, "xls", "xlsx", "ods", "csv", "tsv", "numbers")
            put(PRESENTATION, "ppt", "pptx", "odp", "key")
            put(ARCHIVE, "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "jar", "cbz", "cbr")
            put(APK, "apk", "apks", "xapk", "apkm")
            put(CODE, "kt", "kts", "java", "js", "mjs", "ts", "tsx", "jsx", "py", "c", "h", "cpp", "hpp", "cs", "go", "rs", "rb", "php", "swift", "dart", "sh", "bat", "ps1", "gradle", "lua", "sql", "css", "scss", "html", "htm", "xml", "json", "yaml", "yml", "toml", "gd", "vue", "svelte")
            put(TEXT, "txt", "md", "log", "ini", "cfg", "conf", "properties", "env", "srt", "vtt", "nfo")
            put(EBOOK, "epub", "mobi", "azw", "azw3", "fb2", "djvu")
            put(FONT, "ttf", "otf", "woff", "woff2")
        }

        private val mimeByExt: Map<String, String> = mapOf(
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "gif" to "image/gif",
            "webp" to "image/webp", "bmp" to "image/bmp", "heic" to "image/heic", "heif" to "image/heif",
            "avif" to "image/avif", "svg" to "image/svg+xml",
            "mp4" to "video/mp4", "mkv" to "video/x-matroska", "webm" to "video/webm", "avi" to "video/x-msvideo",
            "mov" to "video/quicktime", "3gp" to "video/3gpp", "m4v" to "video/mp4",
            "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "aac" to "audio/aac", "flac" to "audio/flac",
            "wav" to "audio/wav", "ogg" to "audio/ogg", "opus" to "audio/opus", "amr" to "audio/amr",
            "pdf" to "application/pdf",
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "odt" to "application/vnd.oasis.opendocument.text",
            "ods" to "application/vnd.oasis.opendocument.spreadsheet",
            "csv" to "text/csv", "txt" to "text/plain", "md" to "text/markdown", "log" to "text/plain",
            "html" to "text/html", "htm" to "text/html", "xml" to "text/xml", "json" to "application/json",
            "zip" to "application/zip", "rar" to "application/vnd.rar", "7z" to "application/x-7z-compressed",
            "tar" to "application/x-tar", "gz" to "application/gzip",
            "apk" to "application/vnd.android.package-archive",
            "epub" to "application/epub+zip", "ttf" to "font/ttf", "otf" to "font/otf",
        )

        fun extensionOf(name: String): String {
            val dot = name.lastIndexOf('.')
            return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase(Locale.ROOT)
        }

        fun of(file: File): FileCategory = if (file.isDirectory) FOLDER else ofName(file.name)

        fun ofName(name: String): FileCategory = byExt[extensionOf(name)] ?: OTHER

        fun mimeOf(name: String): String {
            val ext = extensionOf(name)
            mimeByExt[ext]?.let { return it }
            return when (byExt[ext]) {
                IMAGE -> "image/*"
                VIDEO -> "video/*"
                AUDIO -> "audio/*"
                TEXT, CODE -> "text/plain"
                else -> "*/*"
            }
        }

        /** Files we can safely open in the built-in text editor. */
        fun isTextLike(name: String): Boolean = ofName(name) in setOf(TEXT, CODE) ||
            extensionOf(name) in setOf("csv", "tsv", "svg")

        fun extensionsOf(cat: FileCategory): Set<String> =
            byExt.filterValues { it == cat }.keys

        /** Media categories that matter for the storage breakdown. */
        val statCategories = listOf(IMAGE, VIDEO, AUDIO, DOCUMENT, PDF, ARCHIVE, APK, OTHER)
    }
}
