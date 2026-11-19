package com.orbitfiles.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.ui.theme.CategoryColors
import com.orbitfiles.app.ui.theme.LocalCompact
import com.orbitfiles.app.ui.theme.LocalReduceMotion
import com.orbitfiles.app.ui.theme.Motion
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun iconFor(cat: FileCategory): ImageVector = when (cat) {
    FileCategory.FOLDER -> Icons.Rounded.Folder
    FileCategory.IMAGE -> Icons.Rounded.Image
    FileCategory.VIDEO -> Icons.Rounded.Movie
    FileCategory.AUDIO -> Icons.Rounded.AudioFile
    FileCategory.DOCUMENT -> Icons.Rounded.Description
    FileCategory.PDF -> Icons.Rounded.PictureAsPdf
    FileCategory.SPREADSHEET -> Icons.Rounded.TableChart
    FileCategory.PRESENTATION -> Icons.Rounded.Slideshow
    FileCategory.ARCHIVE -> Icons.Rounded.Archive
    FileCategory.APK -> Icons.Rounded.Android
    FileCategory.CODE -> Icons.Rounded.Code
    FileCategory.TEXT -> Icons.AutoMirrored.Rounded.Article
    FileCategory.EBOOK -> Icons.Rounded.AutoStories
    FileCategory.FONT -> Icons.Rounded.FontDownload
    FileCategory.OTHER -> Icons.Rounded.InsertDriveFile
}

/** Loads the launcher icon embedded in an APK file. */
@Composable
private fun rememberApkIcon(file: File): ImageBitmap? {
    val context = LocalContext.current
    val state by produceState<ImageBitmap?>(null, file.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(file.path, 0) ?: return@runCatching null
                val app = info.applicationInfo ?: return@runCatching null
                app.sourceDir = file.path
                app.publicSourceDir = file.path
                val d = app.loadIcon(pm)
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val c = AndroidCanvas(bmp)
                d.setBounds(0, 0, 96, 96)
                d.draw(c)
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    return state
}

/**
 * File/folder glyph. Selected items morph from a rounded square into a circle with a check
 * (shape morph + scale, in the spirit of Material 3 Expressive).
 */
@Composable
fun FileIcon(
    file: File,
    category: FileCategory,
    selected: Boolean = false,
    size: Dp = 46.dp,
    thumbnails: Boolean = true,
) {
    val reduce = LocalReduceMotion.current
    val radius by animateDpAsState(if (selected) size / 2 else size * 0.32f, Motion.bouncy(reduce), label = "morph")
    val primary = MaterialTheme.colorScheme.primary
    val tint = CategoryColors.of(category, primary)
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.size(size).clip(shape).background(if (selected) primary else tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() },
            label = "sel",
        ) { isSel ->
            if (isSel) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, "Selecionado", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.5f))
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(iconFor(category), null, tint = tint, modifier = Modifier.size(size * 0.52f))
                    if (thumbnails && (category == FileCategory.IMAGE || category == FileCategory.VIDEO)) {
                        AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (category == FileCategory.VIDEO) {
                            Box(Modifier.size(size * 0.42f).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.3f))
                            }
                        }
                    } else if (thumbnails && category == FileCategory.APK) {
                        rememberApkIcon(file)?.let { Image(it, null, Modifier.fillMaxSize().padding(size * 0.12f)) }
                    }
                }
            }
        }
    }
}

fun subtitleFor(e: FileEntry): String =
    if (e.isDirectory) {
        val n = e.childCount
        (if (n == null) "Pasta" else if (n == 1) "1 item" else "$n itens") + " · " + Formatters.relative(e.modified)
    } else "${Formatters.size(e.size)} · ${Formatters.relative(e.modified)}"

@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    thumbnails: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = subtitleFor(entry),
    trailing: (@Composable () -> Unit)? = null,
) {
    val compact = LocalCompact.current
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .bouncyClick(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (compact) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(entry.file, entry.category, selected, if (compact) 38.dp else 46.dp, thumbnails)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                color = if (entry.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun FileGridCell(
    entry: FileEntry,
    selected: Boolean,
    thumbnails: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier
            .padding(5.dp)
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .bouncyClick(onLongClick = onLongClick, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val media = thumbnails && (entry.category == FileCategory.IMAGE || entry.category == FileCategory.VIDEO)
        if (media) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium)) {
                AsyncImage(model = entry.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (selected) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                FileIcon(entry.file, entry.category, selected, 64.dp, thumbnails)
            }
        }
        Text(
            entry.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (entry.isDirectory) entry.childCount?.let { "$it itens" } ?: "Pasta" else Formatters.size(entry.size),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
    }
}
