package com.orbitfiles.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.SmartCollection
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.data.StorageVolumeInfo
import com.orbitfiles.app.ui.Dest
import com.orbitfiles.app.ui.FileOpener
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.FileIcon
import com.orbitfiles.app.ui.components.LegendItem
import com.orbitfiles.app.ui.components.RingSegment
import com.orbitfiles.app.ui.components.SectionHeader
import com.orbitfiles.app.ui.components.SegmentRing
import com.orbitfiles.app.ui.components.UsageBar
import com.orbitfiles.app.ui.components.bouncyClick
import com.orbitfiles.app.ui.components.iconFor
import com.orbitfiles.app.ui.theme.CategoryColors
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.Formatters
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    var volumes by mutableStateOf<List<StorageVolumeInfo>>(emptyList())
        private set
    var counts by mutableStateOf<Map<SmartCollection, Pair<Int, Long>>>(emptyMap())
        private set
    var recent by mutableStateOf<List<FileEntry>>(emptyList())
        private set

    init {
        refresh()
        viewModelScope.launch { c.ops.version.collect { refresh() } }
    }

    fun refresh() {
        viewModelScope.launch {
            volumes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.storage.volumes() }
            counts = c.storage.collectionCounts()
            recent = c.storage.recent(24)
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

@Composable
fun HomeScreen(c: AppContainer, vm: HomeViewModel, nav: Navigator, onOpenPalette: () -> Unit) {
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Orbit Files", style = MaterialTheme.typography.displaySmall)
                }
                IconButton(onClick = { nav.push(Dest.Settings) }) { Icon(Icons.Rounded.Settings, "Configurações") }
            }
        }
        item {
            // Command palette entry: search files or type actions ("limpar", "cofre", "wifi"…).
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).bouncyClick(onClick = onOpenPalette),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(14.dp))
                    Text("Buscar arquivos ou digitar um comando", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val primary = vm.volumes.firstOrNull { it.primary }
        if (primary != null) item { StorageHero(primary, vm.counts) { nav.openFolder(primary.root) } }
        val others = vm.volumes.filter { !it.primary }
        items(others, key = { it.root.path }) { v ->
            VolumeCard(v) { nav.openFolder(v.root) }
        }
        item { SectionHeader("Coleções") }
        item {
            val cols = SmartCollection.entries.chunked(2)
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cols.forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { col ->
                            CollectionTile(col, vm.counts[col], Modifier.weight(1f)) {
                                if (col == SmartCollection.DOWNLOADS) nav.openFolder(File(c.storage.primaryRoot, "Download"))
                                else nav.push(Dest.CollectionList(col))
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionHeader("Acesso rápido") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(c.storage.quickFolders()) { (label, dir) ->
                    AssistChip(onClick = { nav.openFolder(dir) }, label = { Text(label) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) }, shape = MaterialTheme.shapes.medium)
                }
            }
        }
        item { SectionHeader("Ferramentas") }
        item {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile("Limpeza", Icons.Rounded.CleaningServices, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) { nav.push(Dest.Cleaner) }
                ToolTile("Análise", Icons.Rounded.PieChart, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { nav.push(Dest.Analyzer(c.storage.primaryRoot)) }
                ToolTile("Cofre", Icons.Rounded.Lock, MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) { nav.push(Dest.Vault) }
                ToolTile("Wi-Fi", Icons.Rounded.Wifi, CategoryColors.of(FileCategory.CODE, MaterialTheme.colorScheme.primary), Modifier.weight(1f)) { nav.push(Dest.Share) }
            }
        }
        if (settings.bookmarks.isNotEmpty()) {
            item { SectionHeader("Favoritos") }
            items(settings.bookmarks, key = { "bm:$it" }) { path ->
                val f = File(path)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(MaterialTheme.shapes.large)
                        .bouncyClick { nav.openFolder(f) }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Star, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name.ifBlank { path }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.storage.displayPath(f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (vm.recent.isNotEmpty()) {
            item { SectionHeader("Recentes", action = "Buscar", onAction = { nav.search("recentes") }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.recent, key = { it.path }) { e ->
                        RecentCard(e, settings.thumbnails) { FileOpener.open(context, nav, e.file, vm.recent.map { it.file }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageHero(v: StorageVolumeInfo, counts: Map<SmartCollection, Pair<Int, Long>>, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val cats = listOf(
        SmartCollection.IMAGES to FileCategory.IMAGE, SmartCollection.VIDEOS to FileCategory.VIDEO, SmartCollection.AUDIO to FileCategory.AUDIO,
        SmartCollection.DOCUMENTS to FileCategory.DOCUMENT, SmartCollection.APKS to FileCategory.APK, SmartCollection.ARCHIVES to FileCategory.ARCHIVE,
    )
    val known = cats.sumOf { counts[it.first]?.second ?: 0L }
    val otherBytes = (v.used - known).coerceAtLeast(0)
    val segments = cats.map { (col, cat) -> RingSegment((counts[col]?.second ?: 0L).toFloat(), CategoryColors.of(cat, primary)) } +
        RingSegment(otherBytes.toFloat(), MaterialTheme.colorScheme.outline)
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).bouncyClick(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            Modifier.background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f))),
            ),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                SegmentRing(segments, Modifier.size(132.dp), stroke = 16.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Formatters.percent(v.used, v.total), style = MaterialTheme.typography.headlineMedium)
                        Text("usado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Smartphone, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(v.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${Formatters.size(v.free)} livres de ${Formatters.size(v.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    cats.take(4).forEach { (col, cat) ->
                        LegendItem(CategoryColors.of(cat, primary), col.label, Formatters.size(counts[col]?.second ?: 0L))
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(v: StorageVolumeInfo, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).bouncyClick(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (v.removable) Icons.Rounded.SdCard else Icons.Rounded.Smartphone, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text(v.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Formatters.percent(v.used, v.total), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(12.dp))
            UsageBar(v.usedFraction, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            Text("${Formatters.size(v.used)} usados · ${Formatters.size(v.free)} livres", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CollectionTile(col: SmartCollection, stat: Pair<Int, Long>?, modifier: Modifier, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val color = if (col == SmartCollection.DOWNLOADS) primary else CategoryColors.of(col.category, primary)
    Surface(modifier.bouncyClick(onClick = onClick), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Icon(if (col == SmartCollection.DOWNLOADS) Icons.Rounded.Download else iconFor(col.category), null, tint = color)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(col.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    stat?.let { "${it.first} · ${Formatters.size(it.second)}" } ?: "…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ToolTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.bouncyClick(onClick = onClick), shape = MaterialTheme.shapes.large, color = color.copy(alpha = 0.13f)) {
        Column(Modifier.padding(vertical = 16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RecentCard(e: FileEntry, thumbnails: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(118.dp).clip(MaterialTheme.shapes.large).bouncyClick(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnails && (e.category == FileCategory.IMAGE || e.category == FileCategory.VIDEO)) {
                AsyncImage(model = e.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else FileIcon(e.file, e.category, size = 56.dp, thumbnails = thumbnails)
        }
        Text(e.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        Text(Formatters.relative(e.modified), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
    }
}
