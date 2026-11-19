package com.orbitfiles.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.orbitfiles.core.FileCategory
import java.io.File

object Intents {
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    /** Opens with an external app (system chooser when [chooser] is true). */
    fun openWith(context: Context, file: File, chooser: Boolean = false, mime: String = FileCategory.mimeOf(file.name)) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(if (chooser) Intent.createChooser(intent, "Abrir com").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
        } catch (_: ActivityNotFoundException) {
            if (mime != "*/*") openWith(context, file, chooser = true, mime = "*/*")
            else toast(context, "Nenhum app encontrado para abrir este arquivo")
        } catch (e: Exception) {
            toast(context, e.message ?: "Não foi possível abrir")
        }
    }

    fun share(context: Context, files: List<File>) {
        val uris = ArrayList(files.filter { it.isFile }.map { uriFor(context, it) })
        if (uris.isEmpty()) { toast(context, "Selecione arquivos (pastas não podem ser compartilhadas)"); return }
        val mimes = files.map { FileCategory.mimeOf(it.name) }.distinct()
        val mime = if (mimes.size == 1) mimes[0] else "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]); type = mime }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris); type = mime }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        context.startActivity(Intent.createChooser(intent, "Compartilhar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            toast(context, "Permita a instalação de apps desta fonte e tente novamente")
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        openWith(context, file, mime = "application/vnd.android.package-archive")
    }

    fun allFilesAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        }

    fun openUrl(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun shareText(context: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        context.startActivity(Intent.createChooser(i, "Compartilhar link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copyText(context: Context, label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < 33) toast(context, "Copiado")
    }

    fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
