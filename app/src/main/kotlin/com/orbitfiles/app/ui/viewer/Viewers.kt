package com.orbitfiles.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.ui.FileOpener
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.ConfirmDialog
import com.orbitfiles.app.ui.components.EmptyState
import com.orbitfiles.app.ui.components.FileRow
import com.orbitfiles.app.ui.components.OrbitScreen
import com.orbitfiles.app.util.Intents
import com.orbitfiles.core.ArchiveEntry
import com.orbitfiles.core.ArchiveManager
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.FileOps
import com.orbitfiles.core.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ------------------------------------------------------------------ Images

@Composable
fun ImageViewer(nav: Navigator, file: File, siblings: List<File>) {
    val context = LocalContext.current
    val list = siblings.ifEmpty { listOf(file) }
    val pager = rememberPagerState(initialPage = list.indexOf(file).coerceAtLeast(0)) { list.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, beyondViewportPageCount = 1, key = { list[it].path }) { page ->
            ZoomableImage(list[page], onTap = { chrome = !chrome }, onZoomChanged = { if (page == pager.currentPage) zoomed = it })
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
            val current = list[pager.currentPage]
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = Color.White, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.titleMedium)
                    Text("${pager.currentPage + 1} de ${list.size} · ${Formatters.size(current.length())}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { Intents.share(context, listOf(current)) }) { Icon(Icons.Rounded.Share, "Compartilhar", tint = Color.White) }
                IconButton(onClick = { Intents.openWith(context, current, chooser = true) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Abrir com", tint = Color.White) }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale > 1f) offset + pan * scale else Offset.Zero
        onZoomChanged(scale > 1.01f)
    }
    AsyncImage(
        model = file, contentDescription = file.name, contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero; onZoomChanged(scale > 1f) },
                )
            }
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
    )
}

// ------------------------------------------------------------------ Text editor

@Composable
fun TextEditor(c: AppContainer, nav: Navigator, file: File, readOnlyInitial: Boolean = false) {
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(!readOnlyInitial && file.canWrite()) }
    var wrap by remember { mutableStateOf(true) }
    var askDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(file) {
        val r = withContext(Dispatchers.IO) { runCatching { file.readText(Charsets.UTF_8) } }
        r.onSuccess { original = it; text = it }.onFailure { error = it.message ?: "Não foi possível ler o arquivo" }
    }
    val dirty = original != null && text != original
    fun save() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { file.writeText(text, Charsets.UTF_8) }.isSuccess }
            if (ok) { original = text; c.ops.emit("Salvo"); c.ops.scan(listOf(file)) } else c.ops.emit("Não foi possível salvar", error = true)
        }
    }
    BackHandler(enabled = dirty && com.orbitfiles.app.ui.LocalIsTopScreen.current) { askDiscard = true }
    OrbitScreen(
        title = file.name + if (dirty) " •" else "",
        subtitle = original?.let { "${text.lines().size} linhas · ${Formatters.size(text.toByteArray().size.toLong())}" },
        large = false,
        onBack = { if (dirty) askDiscard = true else nav.pop() },
        actions = {
            IconButton(onClick = { wrap = !wrap }) { Icon(Icons.AutoMirrored.Rounded.WrapText, "Quebra de linha", tint = if (wrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            if (file.canWrite()) IconButton(onClick = { editing = !editing }) { Icon(Icons.Rounded.Edit, "Editar", tint = if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            if (dirty) IconButton(onClick = ::save) { Icon(Icons.Rounded.Save, "Salvar") }
        },
    ) { padding ->
        when {
            error != null -> Box(Modifier.padding(padding)) { EmptyState(Icons.Rounded.Edit, "Erro ao abrir", error) }
            original == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val h = rememberScrollState()
                val v = rememberScrollState()
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    readOnly = !editing,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(v)
                        .then(if (wrap) Modifier else Modifier.horizontalScroll(h))
                        .padding(16.dp),
                )
            }
        }
    }
    if (askDiscard) ConfirmDialog(
        "Descartar alterações?", "Você tem alterações não salvas em \"${file.name}\".", "Descartar", destructive = true,
        onDismiss = { askDiscard = false },
    ) { askDiscard = false; nav.pop() }
}

// ------------------------------------------------------------------ Video / audio

@OptIn(UnstableApi::class)
@Composable
fun MediaPlayerScreen(nav: Navigator, file: File, playlist: List<File>) {
    val context = LocalContext.current
    val list = playlist.ifEmpty { listOf(file) }
    var title by remember { mutableStateOf(file.name) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(list.map { MediaItem.fromUri(Uri.fromFile(it)) }, list.indexOf(file).coerceAtLeast(0), 0L)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                list.getOrNull(player.currentMediaItemIndex)?.let { title = it.name }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player; setShowNextButton(list.size > 1); setShowPreviousButton(list.size > 1); setBackgroundColor(AColor.BLACK) } },
            modifier = Modifier.fillMaxSize(),
        )
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", tint = Color.White) }
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ------------------------------------------------------------------ PDF

private class PdfDoc(file: File) : AutoCloseable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    val pageCount: Int = renderer.pageCount
    /** Height/width ratio of every page, measured once while loading (off the main thread). */
    val aspects: FloatArray = FloatArray(pageCount) { i -> renderer.openPage(i).use { it.height.toFloat() / it.width } }

    /** PdfRenderer allows only one open page at a time: serialize access. */
    @Synchronized
    fun render(index: Int, widthPx: Int): Bitmap {
        renderer.openPage(index).use { page ->
            val w = widthPx.coerceIn(200, 2400)
            val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        }
    }

    @Synchronized
    override fun close() { runCatching { renderer.close() }; runCatching { pfd.close() } }
}

@Composable
fun PdfViewer(nav: Navigator, file: File) {
    val context = LocalContext.current
    val doc by produceState<Result<PdfDoc>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { PdfDoc(file) } }
    }
    DisposableEffect(doc) { onDispose { doc?.getOrNull()?.close() } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        offsetX = if (scale > 1f) offsetX + pan.x else 0f
    }
    OrbitScreen(
        title = file.name,
        subtitle = doc?.getOrNull()?.let { "${it.pageCount} páginas" },
        large = false,
        onBack = { nav.pop() },
        actions = { IconButton(onClick = { Intents.openWith(context, file, chooser = true) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Abrir com") } },
    ) { padding ->
        val d = doc
        when {
            d == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            d.isFailure -> Box(Modifier.padding(padding)) {
                EmptyState(Icons.AutoMirrored.Rounded.OpenInNew, "Não foi possível abrir o PDF", "O arquivo pode estar protegido por senha. Tente abrir com outro app.", "Abrir com…") {
                    Intents.openWith(context, file, chooser = true)
                }
            }
            else -> {
                val pdf = d.getOrThrow()
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                    LazyColumn(
                        Modifier.fillMaxSize().transformable(state, canPan = { scale > 1f })
                            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX },
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        items(pdf.pageCount) { i ->
                            val bmp by produceState<Bitmap?>(null, i, widthPx) {
                                value = withContext(Dispatchers.IO) { runCatching { pdf.render(i, (widthPx * 1.5f).toInt()) }.getOrNull() }
                            }
                            val ratio = pdf.aspects.getOrElse(i) { 1.414f }
                            Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = MaterialTheme.shapes.small, shadowElevation = 2.dp, color = Color.White) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f / ratio), contentAlignment = Alignment.Center) {
                                    val b = bmp
                                    if (b != null) Image(b.asImageBitmap(), "Página ${i + 1}", Modifier.fillMaxSize())
                                    else CircularProgressIndicator()
                                }
                            }
                            Text("${i + 1}", Modifier.fillMaxWidth().padding(bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Archives

@Composable
fun ArchiveViewer(c: AppContainer, nav: Navigator, file: File) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by produceState<Result<List<ArchiveEntry>>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { ArchiveManager.list(file) } }
    }
    var prefix by remember { mutableStateOf("") }
    BackHandler(enabled = prefix.isNotEmpty() && com.orbitfiles.app.ui.LocalIsTopScreen.current) { prefix = prefix.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } }
    val all = entries?.getOrNull().orEmpty()
    // Build the immediate children of the current prefix (archives may omit directory entries).
    val children = remember(all, prefix) {
        val dirs = linkedSetOf<String>()
        val files = mutableListOf<ArchiveEntry>()
        all.forEach { e ->
            if (!e.path.startsWith(prefix) || e.path == prefix) return@forEach
            val rest = e.path.removePrefix(prefix)
            val slash = rest.indexOf('/')
            if (slash >= 0) dirs += rest.substring(0, slash) else if (!e.isDirectory) files += e
        }
        dirs.sortedBy { it.lowercase() }.map { ArchiveEntry("$prefix$it/", 0, 0, true, 0) } + files.sortedBy { it.name.lowercase() }
    }
    val total = all.sumOf { it.size }
    OrbitScreen(
        title = file.name,
        subtitle = if (prefix.isEmpty()) "${all.count { !it.isDirectory }} arquivos · ${Formatters.size(total)} descompactado" else "/$prefix",
        onBack = { nav.pop() },
        floatingActionButton = {
            if (entries?.isSuccess == true) ExtendedFloatingActionButton(
                onClick = {
                    val parent = file.parentFile ?: return@ExtendedFloatingActionButton
                    val dest = File(parent, FileOps.uniqueName(parent, ArchiveManager.suggestedFolder(file)))
                    c.ops.extract(file, dest, if (prefix.isEmpty()) null else setOf(prefix))
                },
                icon = { Icon(Icons.Rounded.Unarchive, null) },
                text = { Text(if (prefix.isEmpty()) "Extrair tudo" else "Extrair esta pasta") },
                shape = MaterialTheme.shapes.large,
            )
        },
    ) { padding ->
        val r = entries
        when {
            r == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            r.isFailure -> Box(Modifier.padding(padding)) { EmptyState(Icons.Rounded.Unarchive, "Arquivo compactado inválido", r.exceptionOrNull()?.message) }
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 120.dp)) {
                itemsIndexed(children, key = { _, e -> e.path }) { _, e ->
                    val entry = FileEntry(File(e.path), e.name, e.isDirectory, e.size, e.time, false,
                        if (e.isDirectory) FileCategory.FOLDER else FileCategory.ofName(e.name), null)
                    FileRow(
                        entry, false, thumbnails = false,
                        onClick = {
                            if (e.isDirectory) prefix = e.path
                            else scope.launch {
                                val out = withContext(Dispatchers.IO) {
                                    runCatching { ArchiveManager.extractEntry(file, e.path, File(context.cacheDir, "archive_preview/${e.path.hashCode().toUInt()}/${File(e.name).name}")) }.getOrNull()
                                }
                                if (out != null) FileOpener.open(context, nav, out) else c.ops.emit("Não foi possível abrir", error = true)
                            }
                        },
                        onLongClick = {},
                        subtitle = if (e.isDirectory) "Pasta" else "${Formatters.size(e.size)} · compactado ${Formatters.size(e.compressedSize)}",
                    )
                }
            }
        }
    }
}
