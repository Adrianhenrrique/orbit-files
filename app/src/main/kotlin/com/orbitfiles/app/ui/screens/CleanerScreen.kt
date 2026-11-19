package com.orbitfiles.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.ConfirmDialog
import com.orbitfiles.app.ui.components.FileIcon
import com.orbitfiles.app.ui.components.ListRow
import com.orbitfiles.app.ui.components.OrbitScreen
import com.orbitfiles.core.CancelToken
import com.orbitfiles.core.CleanerScanner
import com.orbitfiles.core.DuplicateFinder
import com.orbitfiles.core.DuplicateGroup
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.Formatters
import com.orbitfiles.core.JunkItem
import com.orbitfiles.core.JunkKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun JunkKind.icon(): ImageVector = when (this) {
    JunkKind.TEMP -> Icons.Rounded.AutoDelete
    JunkKind.THUMBNAILS -> Icons.Rounded.Image
    JunkKind.EMPTY_FOLDERS -> Icons.Rounded.FolderOff
    JunkKind.APKS -> Icons.Rounded.InstallMobile
    JunkKind.LARGE -> Icons.Rounded.Storage
    JunkKind.OLD_DOWNLOADS -> Icons.Rounded.Download
}

@Composable
fun CleanerScreen(c: AppContainer, nav: Navigator) {
    val settings by c.settings.settings.collectAsState()
    var junk by remember { mutableStateOf<Map<JunkKind, List<JunkItem>>?>(null) }
    var dupes by remember { mutableStateOf<List<DuplicateGroup>?>(null) }
    var dupeStage by remember { mutableStateOf("") }
    var scannedFiles by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirm by remember { mutableStateOf(false) }
    var runId by remember { mutableIntStateOf(0) }
    val root = c.storage.primaryRoot
    val token = remember(runId) { CancelToken() }
    DisposableEffect(token) { onDispose { token.cancel() } }

    LaunchedEffect(runId) {
        junk = null; dupes = null; selected = emptySet()
        runCatching {
            val j = withContext(Dispatchers.IO) { CleanerScanner.scan(root, token = token) { scannedFiles = it } }
            junk = j
            // Pre-select the safe categories.
            selected = (j[JunkKind.TEMP].orEmpty() + j[JunkKind.THUMBNAILS].orEmpty() + j[JunkKind.EMPTY_FOLDERS].orEmpty()).map { it.file.path }.toSet()
            val d = withContext(Dispatchers.IO) {
                DuplicateFinder.find(listOf(root), minSize = 32 * 1024, token = token) { stage, done, total ->
                    dupeStage = if (total > 0) "$stage $done/$total" else "$stage $done"
                }
            }
            dupes = d
        }
    }

    val sizes = remember(junk, dupes) {
        val m = HashMap<String, Long>()
        junk?.values?.flatten()?.forEach { m[it.file.path] = it.size }
        dupes?.forEach { g -> g.files.forEach { m[it.path] = g.size } }
        m
    }
    val selectedBytes = selected.sumOf { sizes[it] ?: 0L }

    OrbitScreen(
        title = "Limpeza",
        subtitle = if (junk == null) "Analisando…" else "${Formatters.size(selectedBytes)} selecionados",
        onBack = { nav.pop() },
        actions = { IconButton(onClick = { runId++ }) { Icon(Icons.Rounded.Refresh, "Analisar novamente") } },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = { confirm = true },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.CleaningServices, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Liberar ${Formatters.size(selectedBytes)} (${selected.size} itens)")
                    }
                }
            }
        },
    ) { padding ->
        val j = junk
        if (j == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Verificando $scannedFiles arquivos…", style = MaterialTheme.typography.titleMedium)
            }
            return@OrbitScreen
        }
        LazyColumn(contentPadding = padding) {
            JunkKind.entries.forEach { kind ->
                val list = j[kind].orEmpty()
                if (list.isEmpty()) return@forEach
                item(key = kind.name) {
                    val total = list.sumOf { it.size }
                    val paths = list.map { it.file.path }
                    val allSel = paths.all { it in selected }
                    val isOpen = kind.name in expanded
                    Surface(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).animateContentSize(),
                        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column {
                            ListRow(
                                kind.label,
                                "${list.size} itens · ${Formatters.size(total)}\n${kind.description}",
                                kind.icon(),
                                if (kind == JunkKind.LARGE || kind == JunkKind.OLD_DOWNLOADS) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                onClick = { expanded = if (isOpen) expanded - kind.name else expanded + kind.name },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                                    Checkbox(allSel, { selected = if (allSel) selected - paths.toSet() else selected + paths })
                                }
                            }
                            if (isOpen) {
                                HorizontalDivider()
                                list.take(200).forEach { item ->
                                    JunkRow(item.file, item.size, item.file.path in selected) {
                                        selected = if (item.file.path in selected) selected - item.file.path else selected + item.file.path
                                    }
                                }
                                if (list.size > 200) Text("+${list.size - 200} itens", Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            item(key = "dupes") {
                val d = dupes
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).animateContentSize(),
                    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        if (d == null) {
                            ListRow("Arquivos duplicados", "Procurando… $dupeStage", Icons.Rounded.ContentCopy) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                            }
                        } else {
                            val wasted = d.sumOf { it.wasted }
                            val isOpen = "dupes" in expanded
                            ListRow(
                                "Arquivos duplicados",
                                if (d.isEmpty()) "Nenhuma cópia idêntica encontrada" else "${d.size} grupos · ${Formatters.size(wasted)} desperdiçados\nMantemos o mais antigo de cada grupo",
                                Icons.Rounded.ContentCopy,
                                onClick = { expanded = if (isOpen) expanded - "dupes" else expanded + "dupes" },
                            ) {
                                if (d.isNotEmpty()) {
                                    val extras = d.flatMap { it.files.drop(1) }.map { it.path }
                                    val allSel = extras.all { it in selected }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                                        Checkbox(allSel, { selected = if (allSel) selected - extras.toSet() else selected + extras })
                                    }
                                }
                            }
                            if (isOpen) {
                                d.take(100).forEach { g ->
                                    HorizontalDivider()
                                    Text(
                                        "${g.files.size} cópias · ${Formatters.size(g.size)} cada",
                                        Modifier.padding(start = 18.dp, top = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                    )
                                    g.files.forEachIndexed { i, f ->
                                        JunkRow(f, g.size, f.path in selected, label = if (i == 0) "Original (mais antigo)" else null) {
                                            selected = if (f.path in selected) selected - f.path else selected + f.path
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirm) {
        val files = selected.map { File(it) }
        ConfirmDialog(
            title = "Liberar ${Formatters.size(selectedBytes)}?",
            text = if (settings.useTrash) "${files.size} itens irão para a lixeira e podem ser restaurados por ${settings.trashRetentionDays} dias." else "${files.size} itens serão excluídos permanentemente.",
            confirm = "Limpar", destructive = true, icon = Icons.Rounded.CleaningServices,
            onDismiss = { confirm = false },
        ) {
            confirm = false
            c.ops.delete(files)
            // Thumbnails and temp files are disposable; re-scan after the operation finishes.
            selected = emptySet()
            runId++
        }
    }
}

@Composable
private fun JunkRow(file: File, size: Long, checked: Boolean, label: String? = null, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(file, if (file.isDirectory) FileCategory.FOLDER else FileCategory.ofName(file.name), size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                (label?.let { "$it · " } ?: "") + "${Formatters.size(size)} · ${file.parent ?: ""}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Checkbox(checked, { onToggle() })
    }
}
