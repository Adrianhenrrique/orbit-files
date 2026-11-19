package com.orbitfiles.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.ui.components.FileIcon
import com.orbitfiles.app.ui.components.ListRow
import com.orbitfiles.app.util.Intents
import com.orbitfiles.core.ArchiveManager
import com.orbitfiles.core.BatchRenamer
import com.orbitfiles.core.CaseMode
import com.orbitfiles.core.Checksums
import com.orbitfiles.core.ConflictPolicy
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.FileOps
import com.orbitfiles.core.Formatters
import com.orbitfiles.core.RenameRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class FileAction { OPEN_WITH, SHARE, COPY, CUT, RENAME, DELETE, ZIP, EXTRACT, DETAILS, VAULT, BOOKMARK, NEW_TAB, ANALYZE, COPY_PATH }

/** Per-item action sheet (opened from the ⋮ button or long press in single mode). */
@Composable
fun FileActionsSheet(entry: FileEntry, bookmarked: Boolean, onDismiss: () -> Unit, onAction: (FileAction) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FileIcon(entry.file, entry.category, size = 52.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (entry.isDirectory) "Pasta" else "${Formatters.size(entry.size)} · ${entry.category.label}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (!entry.isDirectory) ListRow("Abrir com…", icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = { onAction(FileAction.OPEN_WITH) })
                if (!entry.isDirectory) ListRow("Compartilhar", icon = Icons.Rounded.Share, onClick = { onAction(FileAction.SHARE) })
                ListRow("Copiar", icon = Icons.Rounded.ContentCopy, onClick = { onAction(FileAction.COPY) })
                ListRow("Recortar", icon = Icons.Rounded.ContentCut, onClick = { onAction(FileAction.CUT) })
                ListRow("Renomear", icon = Icons.Rounded.DriveFileRenameOutline, onClick = { onAction(FileAction.RENAME) })
                ListRow("Compactar em ZIP", icon = Icons.Rounded.FolderZip, onClick = { onAction(FileAction.ZIP) })
                if (!entry.isDirectory && entry.category == FileCategory.ARCHIVE && ArchiveManager.isSupported(entry.file)) {
                    ListRow("Extrair aqui", icon = Icons.Rounded.Unarchive, onClick = { onAction(FileAction.EXTRACT) })
                }
                if (!entry.isDirectory) ListRow("Mover para o Cofre", icon = Icons.Rounded.Lock, onClick = { onAction(FileAction.VAULT) })
                if (entry.isDirectory) {
                    ListRow(
                        if (bookmarked) "Remover dos favoritos" else "Adicionar aos favoritos",
                        icon = if (bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        onClick = { onAction(FileAction.BOOKMARK) },
                    )
                    ListRow("Abrir em nova aba", icon = Icons.Rounded.Tab, onClick = { onAction(FileAction.NEW_TAB) })
                    ListRow("Analisar esta pasta", icon = Icons.Rounded.Analytics, onClick = { onAction(FileAction.ANALYZE) })
                }
                ListRow("Copiar caminho", icon = Icons.Rounded.Link, onClick = { onAction(FileAction.COPY_PATH) })
                ListRow("Detalhes", icon = Icons.Rounded.Info, onClick = { onAction(FileAction.DETAILS) })
                ListRow("Excluir", icon = Icons.Rounded.Delete, iconTint = MaterialTheme.colorScheme.error, onClick = { onAction(FileAction.DELETE) })
            }
        }
    }
}

@Composable
fun ConflictDialog(names: List<String>, onDismiss: () -> Unit, onChoose: (ConflictPolicy) -> Unit) {
    var choice by remember { mutableStateOf(ConflictPolicy.KEEP_BOTH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (names.size == 1) "\"${names[0]}\" já existe" else "${names.size} itens já existem") },
        text = {
            Column {
                Text("O que fazer com os itens de mesmo nome no destino?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                listOf(
                    ConflictPolicy.KEEP_BOTH to "Manter ambos (renomear a cópia)",
                    ConflictPolicy.OVERWRITE to "Substituir",
                    ConflictPolicy.SKIP to "Pular",
                ).forEach { (p, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice == p, onClick = { choice = p })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(choice) }) { Text("Continuar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/** Details with lazily computed folder size, image resolution and on-demand checksums. */
@Composable
fun PropertiesSheet(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folderInfo by remember { mutableStateOf<String?>(null) }
    var imageInfo by remember { mutableStateOf<String?>(null) }
    var md5 by remember { mutableStateOf<String?>(null) }
    var sha by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            if (file.isDirectory) {
                val t = runCatching { FileOps.totals(listOf(file)) }.getOrNull()
                folderInfo = t?.let { "${Formatters.size(it.bytes)} · ${it.files} arquivos · ${(it.folders - 1).coerceAtLeast(0)} pastas" }
            } else if (FileCategory.ofName(file.name) == FileCategory.IMAGE) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, o)
                if (o.outWidth > 0) imageInfo = "${o.outWidth} × ${o.outHeight} px"
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Detalhes", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            DetailItem("Nome", file.name)
            DetailItem("Local", file.parent)
            DetailItem("Tipo", if (file.isDirectory) "Pasta" else "${FileCategory.ofName(file.name).label} (${FileCategory.mimeOf(file.name)})")
            DetailItem("Tamanho", if (file.isDirectory) folderInfo ?: "Calculando…" else "${Formatters.size(file.length())} (${"%,d".format(file.length()).replace(',', '.')} bytes)")
            DetailItem("Resolução", imageInfo)
            DetailItem("Modificado", Formatters.dateTime(file.lastModified()))
            DetailItem("Permissões", listOfNotNull(if (file.canRead()) "leitura" else null, if (file.canWrite()) "escrita" else null).joinToString(" + ").ifEmpty { "nenhuma" } + if (file.isHidden) " · oculto" else "")
            if (file.isFile) {
                Text("Verificação de integridade", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                listOf("MD5" to md5, "SHA-256" to sha).forEach { (algo, value) ->
                    if (value != null) {
                        Text(algo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = { Intents.copyText(context, algo, value) }) { Text("Copiar $algo") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (md5 == null) OutlinedButton(onClick = {
                        md5 = "Calculando…"
                        scope.launch { md5 = withContext(Dispatchers.IO) { runCatching { Checksums.hash(file, "MD5") }.getOrElse { "Erro" } } }
                    }) { Text("Calcular MD5") }
                    if (sha == null) OutlinedButton(onClick = {
                        sha = "Calculando…"
                        scope.launch { sha = withContext(Dispatchers.IO) { runCatching { Checksums.hash(file) }.getOrElse { "Erro" } } }
                    }) { Text("Calcular SHA-256") }
                }
            }
        }
    }
}

/** Pattern based multi-file rename with a live, conflict-aware preview. */
@Composable
fun BatchRenameDialog(files: List<File>, onDismiss: () -> Unit, onDone: (List<Pair<File, File>>) -> Unit) {
    var pattern by remember { mutableStateOf("{name}") }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var case by remember { mutableStateOf(CaseMode.KEEP) }
    val rule = RenameRule(pattern.ifBlank { "{name}" }, find, replace, regex, case)
    val preview = remember(rule, files) { BatchRenamer.preview(files, rule) }
    val errors = preview.count { it.error != null }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear ${files.size} itens") },
        text = {
            Column {
                OutlinedTextField(pattern, { pattern = it }, label = { Text("Padrão") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("{name} nome · {n} / {n:3} contador · {date} data · {ext} extensão · {parent} pasta") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(find, { find = it }, label = { Text("Localizar") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(replace, { replace = it }, label = { Text("Substituir") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(regex, { regex = it }); Text("  Expressão regular", style = MaterialTheme.typography.bodyMedium)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(CaseMode.KEEP to "Manter", CaseMode.LOWER to "minúsculas", CaseMode.UPPER to "MAIÚSCULAS", CaseMode.TITLE to "Título").forEach { (m, l) ->
                        FilterChip(selected = case == m, onClick = { case = m }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Prévia", style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(preview.take(200)) { p ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(p.file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "→ ${p.newName}" + (p.error?.let { "  ($it)" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (p.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = errors == 0, onClick = {
                scope.launch {
                    val done = withContext(Dispatchers.IO) { runCatching { BatchRenamer.execute(preview) }.getOrDefault(emptyList()) }
                    onDone(done)
                }
            }) { Text(if (errors > 0) "$errors com erro" else "Renomear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun DetailItem(label: String, value: String?) {
    if (value == null) return
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    Spacer(Modifier.height(10.dp))
}
