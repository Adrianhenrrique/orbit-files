package com.orbitfiles.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.data.SmartCollection
import com.orbitfiles.app.ui.Dest
import com.orbitfiles.app.ui.FileOpener
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.EmptyState
import com.orbitfiles.app.ui.components.FileGridCell
import com.orbitfiles.app.ui.components.FileRow
import com.orbitfiles.app.ui.components.GroupCard
import com.orbitfiles.app.ui.components.ListRow
import com.orbitfiles.app.ui.components.OrbitScreen
import com.orbitfiles.app.ui.components.SectionHeader
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.Formatters

@Composable
fun ToolsScreen(c: AppContainer, nav: Navigator) {
    val trashCount = remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        trashCount.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { c.trash.items().size }.getOrNull() }
    }
    val history by c.ops.history.collectAsState()
    val lan by c.lan.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { Text("Ferramentas", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)) }
        item {
            SectionHeader("Armazenamento")
            GroupCard(Modifier.padding(horizontal = 16.dp)) {
                ListRow("Análise de armazenamento", "Mapa das pastas e maiores arquivos", Icons.Rounded.PieChart, onClick = { nav.push(Dest.Analyzer(c.storage.primaryRoot)) })
                ListRow("Limpeza inteligente", "Duplicados, lixo, downloads antigos, pastas vazias", Icons.Rounded.CleaningServices, MaterialTheme.colorScheme.tertiary, onClick = { nav.push(Dest.Cleaner) })
                ListRow("Lixeira", trashCount.value?.let { if (it == 0) "Vazia" else "$it itens" } ?: "…", Icons.Rounded.Delete, MaterialTheme.colorScheme.error, onClick = { nav.push(Dest.Trash) })
            }
        }
        item {
            SectionHeader("Privacidade e conexão")
            GroupCard(Modifier.padding(horizontal = 16.dp)) {
                ListRow("Cofre criptografado", "AES-256-GCM com senha e biometria", Icons.Rounded.Lock, MaterialTheme.colorScheme.secondary, onClick = { nav.push(Dest.Vault) })
                ListRow("Compartilhar por Wi-Fi", if (lan.running) "Ativo · ${lan.url?.substringBefore("/?")}" else "Acesse seus arquivos pelo navegador do PC", Icons.Rounded.Wifi, onClick = { nav.push(Dest.Share) })
            }
        }
        item {
            SectionHeader("Aplicativo")
            GroupCard(Modifier.padding(horizontal = 16.dp)) {
                ListRow("Histórico e desfazer", if (history.isEmpty()) "Nenhuma ação nesta sessão" else "${history.size} ações recentes", Icons.Rounded.History, onClick = { nav.push(Dest.History) })
                ListRow("Configurações", "Tema, cores, comportamento e segurança", Icons.Rounded.Settings, onClick = { nav.push(Dest.Settings) })
            }
        }
    }
}

/** MediaStore powered smart collection (images, videos, documents…). */
@Composable
fun CollectionScreen(c: AppContainer, nav: Navigator, collection: SmartCollection) {
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsState()
    var items by remember { mutableStateOf<List<FileEntry>?>(null) }
    var sortBySize by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val version by c.ops.version.collectAsState()
    LaunchedEffect(collection, version) { items = c.storage.collection(collection) }
    val sorted = items?.let { list -> if (sortBySize) list.sortedByDescending { it.size } else list }
    val total = items?.sumOf { it.size } ?: 0L
    val media = collection.category == FileCategory.IMAGE || collection.category == FileCategory.VIDEO
    OrbitScreen(
        title = collection.label,
        subtitle = items?.let { "${it.size} arquivos · ${Formatters.size(total)}" },
        onBack = { nav.pop() },
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Ordenar") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Mais recentes") }, onClick = { sortBySize = false; menu = false })
                    DropdownMenuItem(text = { Text("Maiores primeiro") }, onClick = { sortBySize = true; menu = false })
                }
            }
        },
    ) { padding ->
        val list = sorted
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState(Icons.Rounded.FolderOff, "Nada por aqui", "Nenhum arquivo deste tipo foi encontrado.") }
            media -> LazyVerticalGrid(GridCells.Adaptive(110.dp), contentPadding = padding, modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                items(list, key = { it.path }) { e ->
                    FileGridCell(e, false, settings.thumbnails, { FileOpener.open(context, nav, e.file, list.map { it.file }) }, { e.file.parentFile?.let(nav::openFolder) })
                }
            }
            else -> LazyColumn(contentPadding = padding) {
                items(list, key = { it.path }) { e ->
                    FileRow(
                        e, false, settings.thumbnails,
                        onClick = { FileOpener.open(context, nav, e.file, list.map { it.file }) },
                        onLongClick = { e.file.parentFile?.let(nav::openFolder) },
                        subtitle = "${Formatters.size(e.size)} · ${c.storage.displayPath(e.file.parentFile ?: e.file)}",
                    )
                }
            }
        }
    }
}
