package com.orbitfiles.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.data.ViewMode
import com.orbitfiles.app.ops.OpKind
import com.orbitfiles.app.ui.Dest
import com.orbitfiles.app.ui.FileOpener
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.ConfirmDialog
import com.orbitfiles.app.ui.components.EmptyState
import com.orbitfiles.app.ui.components.FileGridCell
import com.orbitfiles.app.ui.components.FileRow
import com.orbitfiles.app.ui.components.InputDialog
import com.orbitfiles.app.util.Intents
import com.orbitfiles.core.ArchiveManager
import com.orbitfiles.core.ConflictPolicy
import com.orbitfiles.core.FileOps
import com.orbitfiles.core.JournalEntry
import com.orbitfiles.core.SortBy
import kotlinx.coroutines.launch
import java.io.File

private sealed interface BrowserDialog {
    data class NewFolder(val dir: File) : BrowserDialog
    data class NewFile(val dir: File) : BrowserDialog
    data class Rename(val file: File) : BrowserDialog
    data class BatchRename(val files: List<File>) : BrowserDialog
    data class Delete(val files: List<File>) : BrowserDialog
    data class Conflict(val dest: File, val names: List<String>, val sources: List<File>, val move: Boolean) : BrowserDialog
    data class Zip(val files: List<File>, val dir: File) : BrowserDialog
    data class Details(val file: File) : BrowserDialog
    data class Actions(val entry: FileEntry, val pane: PaneState) : BrowserDialog
}

@Composable
fun BrowserScreen(c: AppContainer, vm: BrowserViewModel, nav: Navigator, overlayVisible: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsState()
    val clipboard by c.ops.clipboard.collectAsState()
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }

    // Requests from other screens ("open this folder").
    LaunchedEffect(nav.openFolderRequest) {
        nav.openFolderRequest?.let { vm.openRequested(it); nav.openFolderRequest = null }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val p = vm.current

        // Back: clear selection → close filter → go up → history.
        BackHandler(enabled = !overlayVisible && (p.selectionMode || p.filter.isNotEmpty() || vm.canGoUp(p))) {
            when {
                p.selectionMode -> vm.clearSelection(p)
                p.filter.isNotEmpty() -> p.filter = ""
                else -> vm.up(p)
            }
        }

        fun requestPaste(dest: File) {
            val clip = clipboard ?: return
            val conflicts = c.ops.conflictsFor(clip.files, dest)
            if (conflicts.isEmpty()) c.ops.paste(dest, ConflictPolicy.KEEP_BOTH)
            else dialog = BrowserDialog.Conflict(dest, conflicts, clip.files, clip.cut)
        }

        fun transferToOther(files: List<File>, move: Boolean, dest: File) {
            val conflicts = c.ops.conflictsFor(files, dest)
            if (conflicts.isEmpty()) {
                if (move) c.ops.move(files, dest) else c.ops.copy(files, dest)
            } else dialog = BrowserDialog.Conflict(dest, conflicts, files, move)
        }

        fun moveToVault(files: List<File>) {
            if (!c.vault.unlocked.value) {
                c.ops.emit("Desbloqueie o Cofre primeiro para adicionar arquivos")
                nav.push(Dest.Vault)
                return
            }
            c.ops.run(OpKind.VAULT, "Protegendo ${files.size} ${if (files.size == 1) "arquivo" else "arquivos"}") { task, progress ->
                val total = files.sumOf { it.length() }
                var done = 0L
                files.forEachIndexed { i, f ->
                    c.vault.add(f, task.token) { n -> done += n; progress(com.orbitfiles.core.Progress(done, total, i, files.size, f.name)) }
                    FileOps.deleteRecursively(f)
                }
                c.ops.scan(files)
                "${files.size} ${if (files.size == 1) "arquivo protegido" else "arquivos protegidos"} no Cofre" to JournalEntry.Info("Movido para o Cofre: ${JournalEntry.describe(files)}")
            }
        }

        fun handleAction(action: FileAction, files: List<File>, pane: PaneState) {
            dialog = null
            val first = files.firstOrNull() ?: return
            when (action) {
                FileAction.OPEN_WITH -> Intents.openWith(context, first, chooser = true)
                FileAction.SHARE -> Intents.share(context, files)
                FileAction.COPY -> { c.ops.setClipboard(files, cut = false); vm.clearSelection(pane) }
                FileAction.CUT -> { c.ops.setClipboard(files, cut = true); vm.clearSelection(pane) }
                FileAction.RENAME -> dialog = if (files.size == 1) BrowserDialog.Rename(first) else BrowserDialog.BatchRename(files)
                FileAction.DELETE -> if (settings.confirmDelete || !settings.useTrash) dialog = BrowserDialog.Delete(files) else { c.ops.delete(files); vm.clearSelection(pane) }
                FileAction.ZIP -> dialog = BrowserDialog.Zip(files, pane.dir)
                FileAction.EXTRACT -> {
                    val dest = File(first.parentFile, FileOps.uniqueName(first.parentFile!!, ArchiveManager.suggestedFolder(first)))
                    c.ops.extract(first, dest)
                }
                FileAction.DETAILS -> dialog = BrowserDialog.Details(first)
                FileAction.VAULT -> { moveToVault(files.filter { it.isFile }); vm.clearSelection(pane) }
                FileAction.BOOKMARK -> vm.toggleBookmark(first)
                FileAction.NEW_TAB -> vm.newTab(first)
                FileAction.ANALYZE -> nav.push(Dest.Analyzer(first))
                FileAction.COPY_PATH -> Intents.copyText(context, "Caminho", first.absolutePath)
            }
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            // System bar insets are handled by the top app bar and the app's navigation bar.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                AnimatedContent(p.selectionMode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bar") { selecting ->
                    if (selecting) SelectionTopBar(p, vm)
                    else BrowserTopBar(c, vm, p, vm.prefFor(p.dir), settings.showHidden, wide, settings.bookmarks.contains(p.dir.absolutePath),
                        onNewTab = { vm.newTab() }, onAnalyze = { nav.push(Dest.Analyzer(p.dir)) })
                }
            },
            bottomBar = {
                AnimatedVisibility(p.selectionMode, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    val files = p.selectedFiles()
                    SelectionBottomBar(
                        count = files.size,
                        wide = wide && vm.secondary in vm.tabs.indices,
                        onAction = { handleAction(it, files, p) },
                        onCopyToOther = { vm.pane(vm.secondary)?.let { o -> transferToOther(files, false, o.dir); vm.clearSelection(p) } },
                        onMoveToOther = { vm.pane(vm.secondary)?.let { o -> transferToOther(files, true, o.dir); vm.clearSelection(p) } },
                    )
                }
            },
            floatingActionButton = {
                if (!p.selectionMode) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        FloatingActionButton(onClick = { open = true }, shape = MaterialTheme.shapes.large) { Icon(Icons.Rounded.Add, "Criar") }
                        DropdownMenu(open, { open = false }, shape = MaterialTheme.shapes.large) {
                            DropdownMenuItem(text = { Text("Nova pasta") }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) }, onClick = { open = false; dialog = BrowserDialog.NewFolder(p.dir) })
                            DropdownMenuItem(text = { Text("Novo arquivo de texto") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, null) }, onClick = { open = false; dialog = BrowserDialog.NewFile(p.dir) })
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (vm.tabs.size > 1) TabStrip(vm)
                Row(Modifier.weight(1f)) {
                    val primaryIndex = vm.active.coerceIn(0, vm.tabs.lastIndex)
                    Box(Modifier.weight(1f)) {
                        key(vm.tabs[primaryIndex].id) {
                            PaneView(c, vm, nav, vm.tabs[primaryIndex], vm.prefFor(vm.tabs[primaryIndex].dir).viewMode, settings.thumbnails, focused = true,
                                onFocus = {}, onMore = { e -> dialog = BrowserDialog.Actions(e, vm.tabs[primaryIndex]) })
                        }
                    }
                    if (wide) {
                        LaunchedEffect(Unit) { vm.ensureSecondary() }
                        val sec = vm.pane(vm.secondary)?.takeIf { vm.secondary != vm.active }
                        if (sec != null) {
                            VerticalDivider()
                            Box(Modifier.weight(1f)) {
                                key(sec.id) {
                                    PaneView(c, vm, nav, sec, vm.prefFor(sec.dir).viewMode, settings.thumbnails, focused = false,
                                        onFocus = { val a = vm.active; vm.active = vm.secondary; vm.secondary = a },
                                        onMore = { e -> dialog = BrowserDialog.Actions(e, sec) })
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(clipboard != null && !p.selectionMode, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    val clip = clipboard
                    if (clip != null) {
                        Surface(
                            Modifier.fillMaxWidth().padding(12.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (clip.cut) Icons.Rounded.ContentCut else Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "${clip.files.size} ${if (clip.files.size == 1) "item" else "itens"} para ${if (clip.cut) "mover" else "copiar"}",
                                    Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = { c.ops.clearClipboard() }) { Text("Cancelar") }
                                FilledTonalButton(onClick = { requestPaste(p.dir) }) {
                                    Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Colar aqui")
                                }
                            }
                        }
                    }
                }
            }
        }

        when (val d = dialog) {
            null -> Unit
            is BrowserDialog.NewFolder -> InputDialog("Nova pasta", "", confirm = "Criar", selectBaseName = false, validate = { FileOps.validateName(it) ?: if (File(d.dir, it).exists()) "Já existe" else null },
                onDismiss = { dialog = null }) { name -> dialog = null; scope.launch { c.ops.createFolder(d.dir, name) } }
            is BrowserDialog.NewFile -> InputDialog("Novo arquivo", "nota.txt", confirm = "Criar", validate = { FileOps.validateName(it) ?: if (File(d.dir, it).exists()) "Já existe" else null },
                onDismiss = { dialog = null }) { name ->
                dialog = null
                scope.launch { c.ops.createFile(d.dir, name)?.let { FileOpener.open(context, nav, it) } }
            }
            is BrowserDialog.Rename -> InputDialog("Renomear", d.file.name, confirm = "Renomear", selectBaseName = !d.file.isDirectory,
                validate = { FileOps.validateName(it) ?: if (it != d.file.name && !it.equals(d.file.name, true) && File(d.file.parentFile, it).exists()) "Já existe" else null },
                onDismiss = { dialog = null }) { name -> dialog = null; vm.clearSelection(p); scope.launch { c.ops.rename(d.file, name) } }
            is BrowserDialog.BatchRename -> BatchRenameDialog(d.files, onDismiss = { dialog = null }) { done ->
                dialog = null; vm.clearSelection(p); c.ops.recordBatchRename(done)
            }
            is BrowserDialog.Delete -> {
                val permanent = !settings.useTrash
                ConfirmDialog(
                    title = if (permanent) "Excluir permanentemente?" else "Mover para a lixeira?",
                    text = (if (d.files.size == 1) "\"${d.files[0].name}\"" else "${d.files.size} itens") +
                        if (permanent) " serão apagados para sempre." else " poderão ser restaurados por ${settings.trashRetentionDays} dias.",
                    confirm = if (permanent) "Excluir" else "Mover",
                    destructive = true, icon = Icons.Rounded.Delete,
                    onDismiss = { dialog = null },
                ) { dialog = null; c.ops.delete(d.files); vm.clearSelection(p) }
            }
            is BrowserDialog.Conflict -> ConflictDialog(d.names, onDismiss = { dialog = null }) { policy ->
                dialog = null
                if (clipboard?.files == d.sources) c.ops.paste(d.dest, policy)
                else if (d.move) c.ops.move(d.sources, d.dest, policy) else c.ops.copy(d.sources, d.dest, policy)
            }
            is BrowserDialog.Zip -> {
                val suggested = (if (d.files.size == 1) d.files[0].nameWithoutExtension.ifBlank { d.files[0].name } else d.dir.name.ifBlank { "arquivos" }) + ".zip"
                InputDialog("Compactar em ZIP", FileOps.uniqueName(d.dir, suggested), confirm = "Compactar",
                    validate = { FileOps.validateName(it) ?: if (File(d.dir, it).exists()) "Já existe" else null },
                    onDismiss = { dialog = null }) { name ->
                    dialog = null; vm.clearSelection(p)
                    c.ops.zip(d.files, File(d.dir, if (name.endsWith(".zip", true)) name else "$name.zip"))
                }
            }
            is BrowserDialog.Details -> PropertiesSheet(d.file) { dialog = null }
            is BrowserDialog.Actions -> FileActionsSheet(d.entry, settings.bookmarks.contains(d.entry.path), onDismiss = { dialog = null }) { a ->
                handleAction(a, listOf(d.entry.file), d.pane)
            }
        }
    }
}

@Composable
private fun TabStrip(vm: BrowserViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(vm.tabs, key = { _, t -> t.id }) { i, t ->
            InputChip(
                selected = i == vm.active,
                onClick = { vm.active = i },
                label = { Text(t.dir.name.ifBlank { "Armazenamento" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Tab, null, Modifier.size(18.dp)) },
                trailingIcon = {
                    Icon(Icons.Rounded.Close, "Fechar aba", Modifier.size(18.dp).clip(CircleShape).clickable { vm.closeTab(i) })
                },
                shape = MaterialTheme.shapes.medium,
            )
        }
        item {
            IconButton(onClick = { vm.newTab() }) { Icon(Icons.Rounded.Add, "Nova aba") }
        }
    }
}

@Composable
private fun BrowserTopBar(
    c: AppContainer,
    vm: BrowserViewModel,
    p: PaneState,
    pref: com.orbitfiles.app.data.FolderPref,
    showHidden: Boolean,
    wide: Boolean,
    bookmarked: Boolean,
    onNewTab: () -> Unit,
    onAnalyze: () -> Unit,
) {
    var filtering by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            if (filtering) {
                TextField(
                    value = p.filter,
                    onValueChange = { p.filter = it },
                    placeholder = { Text("Filtrar nesta pasta") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column {
                    Text(c.storage.volumeFor(p.dir)?.takeIf { it.root.path == p.dir.absolutePath }?.name ?: p.dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (p.loading) "Carregando…" else "${p.entries.size} ${if (p.entries.size == 1) "item" else "itens"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            if (filtering) IconButton(onClick = { filtering = false; p.filter = "" }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Fechar filtro") }
            else if (vm.canGoUp(p)) IconButton(onClick = { vm.up(p) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Pasta acima") }
        },
        actions = {
            if (!filtering) IconButton(onClick = { filtering = true }) { Icon(Icons.Rounded.FilterList, "Filtrar") }
            IconButton(onClick = { vm.setView(p, view = if (pref.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST) }) {
                Icon(if (pref.viewMode == ViewMode.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList, "Alternar visualização")
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Ordenar") }
                DropdownMenu(sortMenu, { sortMenu = false }, shape = MaterialTheme.shapes.large) {
                    SortBy.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label, fontWeight = if (pref.sortBy == s) FontWeight.Bold else FontWeight.Normal) },
                            trailingIcon = { if (pref.sortBy == s) Icon(Icons.Rounded.Check, null) },
                            onClick = { sortMenu = false; vm.setView(p, sortBy = s) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(if (pref.ascending) "Crescente ↑" else "Decrescente ↓") }, onClick = { sortMenu = false; vm.setView(p, ascending = !pref.ascending) })
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Mais") }
                DropdownMenu(menu, { menu = false }, shape = MaterialTheme.shapes.large) {
                    DropdownMenuItem(
                        text = { Text(if (showHidden) "Esconder ocultos" else "Mostrar ocultos") },
                        leadingIcon = { Icon(if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) },
                        onClick = { menu = false; vm.toggleHidden() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (bookmarked) "Remover dos favoritos" else "Favoritar pasta") },
                        leadingIcon = { Icon(if (bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                        onClick = { menu = false; vm.toggleBookmark(p.dir) },
                    )
                    DropdownMenuItem(text = { Text("Nova aba") }, leadingIcon = { Icon(Icons.Rounded.Tab, null) }, onClick = { menu = false; onNewTab() })
                    if (!wide) DropdownMenuItem(text = { Text("Analisar armazenamento aqui") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { menu = false; onAnalyze() })
                    if (wide) DropdownMenuItem(text = { Text("Trocar painéis") }, leadingIcon = { Icon(Icons.Rounded.VerticalSplit, null) }, onClick = {
                        menu = false
                        if (vm.secondary in vm.tabs.indices) { val a = vm.active; vm.active = vm.secondary; vm.secondary = a }
                    })
                    DropdownMenuItem(text = { Text("Selecionar tudo") }, leadingIcon = { Icon(Icons.Rounded.SelectAll, null) }, onClick = { menu = false; vm.selectAll(p) })
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SelectionTopBar(p: PaneState, vm: BrowserViewModel) {
    TopAppBar(
        title = { Text("${p.selection.size} ${if (p.selection.size == 1) "selecionado" else "selecionados"}") },
        navigationIcon = { IconButton(onClick = { vm.clearSelection(p) }) { Icon(Icons.Rounded.Close, "Limpar seleção") } },
        actions = {
            TextButton(onClick = { vm.invertSelection(p) }) { Text("Inverter") }
            IconButton(onClick = { vm.selectAll(p) }) { Icon(Icons.Rounded.SelectAll, "Selecionar tudo") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

@Composable
private fun SelectionBottomBar(
    count: Int,
    wide: Boolean,
    onAction: (FileAction) -> Unit,
    onCopyToOther: () -> Unit,
    onMoveToOther: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        BarAction(Icons.Rounded.ContentCopy, "Copiar") { onAction(FileAction.COPY) }
        BarAction(Icons.Rounded.ContentCut, "Recortar") { onAction(FileAction.CUT) }
        BarAction(Icons.Rounded.Share, "Enviar") { onAction(FileAction.SHARE) }
        BarAction(Icons.Rounded.Delete, "Excluir") { onAction(FileAction.DELETE) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { more = true }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.MoreVert, "Mais")
                Text("Mais", style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(more, { more = false }, shape = MaterialTheme.shapes.large) {
                DropdownMenuItem(text = { Text(if (count == 1) "Renomear" else "Renomear em lote") }, leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) }, onClick = { more = false; onAction(FileAction.RENAME) })
                DropdownMenuItem(text = { Text("Compactar em ZIP") }, leadingIcon = { Icon(Icons.Rounded.FolderZip, null) }, onClick = { more = false; onAction(FileAction.ZIP) })
                DropdownMenuItem(text = { Text("Mover para o Cofre") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) }, onClick = { more = false; onAction(FileAction.VAULT) })
                if (count == 1) DropdownMenuItem(text = { Text("Detalhes") }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { more = false; onAction(FileAction.DETAILS) })
                if (wide) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Copiar para o outro painel") }, leadingIcon = { Icon(Icons.Rounded.VerticalSplit, null) }, onClick = { more = false; onCopyToOther() })
                    DropdownMenuItem(text = { Text("Mover para o outro painel") }, leadingIcon = { Icon(Icons.Rounded.VerticalSplit, null) }, onClick = { more = false; onMoveToOther() })
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** Breadcrumb trail from the volume root to the current folder. */
@Composable
private fun Breadcrumbs(c: AppContainer, vm: BrowserViewModel, p: PaneState) {
    val vol = remember(p.dir) { c.storage.volumeFor(p.dir) }
    val volumes = remember { c.storage.volumes() }
    val crumbs = remember(p.dir) {
        val list = mutableListOf<Pair<String, File>>()
        var f: File? = p.dir
        while (f != null) {
            val isRoot = vol != null && f.absolutePath == vol.root.path
            list.add(0, (if (isRoot) vol!!.name else f.name) to f)
            if (isRoot) break
            f = f.parentFile
        }
        list
    }
    val state = rememberLazyListState()
    LaunchedEffect(crumbs.size) { if (crumbs.isNotEmpty()) state.animateScrollToItem(crumbs.lastIndex) }
    var volMenu by remember { mutableStateOf(false) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        itemsIndexed(crumbs) { i, (label, file) ->
            val last = i == crumbs.lastIndex
            Box {
                Surface(
                    onClick = { if (i == 0 && volumes.size > 1) volMenu = true else vm.navigate(p, file) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (last) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    border = if (last) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (i == 0) {
                            Icon(if (vol?.removable == true) Icons.Rounded.SdCard else Icons.Rounded.Smartphone, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
                if (i == 0) DropdownMenu(volMenu, { volMenu = false }, shape = MaterialTheme.shapes.large) {
                    volumes.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.name) },
                            leadingIcon = { Icon(if (v.removable) Icons.Rounded.SdCard else Icons.Rounded.Smartphone, null) },
                            onClick = { volMenu = false; vm.navigate(p, v.root) },
                        )
                    }
                }
            }
            if (!last) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PaneView(
    c: AppContainer,
    vm: BrowserViewModel,
    nav: Navigator,
    p: PaneState,
    viewMode: ViewMode,
    thumbnails: Boolean,
    focused: Boolean,
    onFocus: () -> Unit,
    onMore: (FileEntry) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().then(if (!focused) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f)) else Modifier)) {
        if (!focused) {
            Text(
                "Toque para focar este painel",
                Modifier.fillMaxWidth().clickable(onClick = onFocus).statusBarsPadding().padding(12.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
        }
        Breadcrumbs(c, vm, p)
        PullToRefreshBox(isRefreshing = p.refreshing, onRefresh = { vm.refresh(p) }, modifier = Modifier.weight(1f)) {
            val onClick: (FileEntry) -> Unit = { e ->
                if (!focused) onFocus()
                when {
                    p.selectionMode -> vm.toggleSelect(p, e)
                    e.isDirectory -> vm.navigate(p, e.file)
                    else -> FileOpener.open(context, nav, e.file, p.visible.filter { !it.isDirectory }.map { it.file })
                }
            }
            val onLong: (FileEntry) -> Unit = { e -> vm.toggleSelect(p, e) }
            AnimatedContent(
                targetState = p.dir.absolutePath to p.loading,
                transitionSpec = {
                    val forward = targetState.first.length >= initialState.first.length
                    (slideInHorizontally { if (forward) it / 6 else -it / 6 } + fadeIn()) togetherWith fadeOut()
                },
                contentKey = { it.first },
                label = "folder",
            ) { (_, loading) ->
                when {
                    loading && p.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    p.error != null -> EmptyState(Icons.Rounded.FolderOff, "Não foi possível abrir", p.error, "Tentar de novo") { vm.load(p) }
                    p.visible.isEmpty() -> EmptyState(
                        Icons.Rounded.FolderOff,
                        if (p.filter.isNotBlank()) "Nada corresponde a \"${p.filter}\"" else "Pasta vazia",
                        if (p.filter.isNotBlank()) null else "Use o botão + para criar algo aqui",
                    )
                    viewMode == ViewMode.GRID -> {
                        val grid = rememberLazyGridState()
                        LazyVerticalGrid(GridCells.Adaptive(112.dp), state = grid, contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                            items(p.visible, key = { it.path }) { e ->
                                FileGridCell(e, e.path in p.selection, thumbnails, { onClick(e) }, { onLong(e) }, Modifier.animateItem())
                            }
                        }
                    }
                    else -> {
                        val saved = p.scroll[p.dir.absolutePath]
                        val list = rememberLazyListState(saved?.first ?: 0, saved?.second ?: 0)
                        LaunchedEffect(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) {
                            p.scroll[p.dir.absolutePath] = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
                        }
                        LazyColumn(state = list, contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize()) {
                            items(p.visible, key = { it.path }) { e ->
                                FileRow(
                                    e, e.path in p.selection, thumbnails, { onClick(e) }, { onLong(e) }, Modifier.animateItem(),
                                    trailing = if (p.selectionMode) null else ({
                                        IconButton(onClick = { onMore(e) }) { Icon(Icons.Rounded.MoreVert, "Ações", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
