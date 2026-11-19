package com.orbitfiles.app.ui

import android.content.Context
import com.orbitfiles.app.util.Intents
import com.orbitfiles.core.ArchiveManager
import com.orbitfiles.core.FileCategory
import java.io.File

/** Decides whether a file opens in a built-in viewer or an external app. */
object FileOpener {
    fun open(context: Context, nav: Navigator, file: File, siblings: List<File> = emptyList()) {
        val cat = FileCategory.ofName(file.name)
        when {
            file.isDirectory -> nav.openFolder(file)
            cat == FileCategory.IMAGE && FileCategory.extensionOf(file.name) != "svg" -> {
                val list = siblings.filter { FileCategory.ofName(it.name) == FileCategory.IMAGE }.ifEmpty { listOf(file) }
                nav.push(Dest.ImageViewer(file, if (file in list) list else listOf(file)))
            }
            cat == FileCategory.VIDEO || cat == FileCategory.AUDIO -> {
                val list = siblings.filter { FileCategory.ofName(it.name) == cat }.ifEmpty { listOf(file) }
                nav.push(Dest.MediaPlayer(file, if (file in list) list else listOf(file)))
            }
            cat == FileCategory.PDF -> nav.push(Dest.PdfViewer(file))
            cat == FileCategory.APK -> Intents.installApk(context, file)
            cat == FileCategory.ARCHIVE && ArchiveManager.isSupported(file) -> nav.push(Dest.ArchiveViewer(file))
            FileCategory.isTextLike(file.name) && file.length() <= 5L * 1024 * 1024 -> nav.push(Dest.TextEditor(file))
            else -> Intents.openWith(context, file)
        }
    }
}
