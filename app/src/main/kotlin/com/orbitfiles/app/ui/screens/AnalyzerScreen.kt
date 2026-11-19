package com.orbitfiles.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.ui.FileOpener
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.FileRow
import com.orbitfiles.app.ui.components.LegendItem
import com.orbitfiles.app.ui.components.OrbitScreen
import com.orbitfiles.app.ui.components.RingSegment
import com.orbitfiles.app.ui.components.SectionHeader
import com.orbitfiles.app.ui.components.SegmentRing
import com.orbitfiles.app.ui.components.UsageBar
import com.orbitfiles.app.ui.components.bouncyClick
import com.orbitfiles.app.ui.theme.CategoryColors
import com.orbitfiles.core.CancelToken
import com.orbitfiles.core.DirNode
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.Formatters
import com.orbitfiles.core.StorageAnalyzer
import com.orbitfiles.core.StorageReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AnalyzerScreen(c: AppContainer, nav: Navigator, root: File) {
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsState()
    var report by remember { mutableStateOf<StorageReport?>(null) }
    var node by remember { mutableStateOf<DirNode?>(null) }
    var scanned by remember { mutableIntStateOf(0) }
    var current by remember { mutableStateOf("") }
    var runId by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val token = remember(runId) { CancelToken() }
    DisposableEffect(token) { onDispose { token.cancel() } }
    LaunchedEffect(runId) {
        report = null; error = null
        try {
            val r = withContext(Dispatchers.IO) {
                StorageAnalyzer.scan(root, 80, token) { n, path -> scanned = n; current = path }
            }
            report = r; node = r.root
        } catch (e: Exception) {
            if (!token.isCancelled) error = e.message
        }
    }
    BackHandler(enabled = node?.parent != null && com.orbitfiles.app.ui.LocalIsTopScreen.current) { node = node?.parent }
    val primary = MaterialTheme.colorScheme.primary
    OrbitScreen(
        title = "Análise",
        subtitle = c.storage.displayPath(node?.file ?: root),
        onBack = { nav.pop() },
        actions = { IconButton(onClick = { runId++ }) { Icon(Icons.Rounded.Refresh, "Analisar novamente") } },
    ) { padding ->
        val r = report
        val n = node
        if (r == null || n == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val err = error
                if (err != null) Text(err, color = MaterialTheme.colorScheme.error)
                else {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(20.dp))
                    Text("Mapeando… ${"%,d".format(scanned).replace(',', '.')} arquivos", style = MaterialTheme.typography.titleMedium)
                    Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
            return@OrbitScreen
        }
        LazyColumn(contentPadding = padding) {
            if (n === r.root) {
                item {
                    val cats = r.byCategory.entries.sortedByDescending { it.value }
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            SegmentRing(cats.map { RingSegment(it.value.toFloat(), CategoryColors.of(it.key, primary)) }, Modifier.size(140.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(Formatters.size(r.root.size), style = MaterialTheme.typography.titleLarge)
                                    Text("${r.totalFiles} arquivos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                cats.take(6).forEach { (cat, bytes) -> LegendItem(CategoryColors.of(cat, primary), if (cat == FileCategory.OTHER) "Outros" else cat.label, Formatters.size(bytes)) }
                                Text("Varredura em ${r.scanMillis / 1000.0}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { SectionHeader(if (n === r.root) "Pastas que mais ocupam" else "Dentro de ${n.name}", action = "Abrir", onAction = { nav.openFolder(n.file) }) }
            val maxSize: Long = n.children.firstOrNull()?.size?.coerceAtLeast(1L) ?: 1L
            items(n.children.take(60), key = { it.file.path }) { child ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).bouncyClick(onLongClick = { nav.openFolder(child.file) }) { if (child.children.isNotEmpty()) node = child else nav.openFolder(child.file) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).background(primary.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(if (child.children.isEmpty()) Icons.Rounded.Folder else Icons.Rounded.FolderOpen, null, tint = primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(child.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(Formatters.size(child.size), style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(6.dp))
                        UsageBar(child.size.toFloat() / maxSize, height = 6.dp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${child.fileCount} arquivos · ${Formatters.percent(child.size, r.root.size.coerceAtLeast(1L))} do total",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (n === r.root) {
                item { SectionHeader("Maiores arquivos") }
                items(r.largestFiles.take(40), key = { "big:" + it.path }) { f ->
                    val e = FileEntry.of(f, countChildren = false)
                    FileRow(
                        e, false, settings.thumbnails,
                        onClick = { FileOpener.open(context, nav, f) },
                        onLongClick = { f.parentFile?.let(nav::openFolder) },
                        subtitle = "${Formatters.size(e.size)} · ${c.storage.displayPath(f.parentFile ?: f)}",
                    )
                }
            }
        }
    }
}
