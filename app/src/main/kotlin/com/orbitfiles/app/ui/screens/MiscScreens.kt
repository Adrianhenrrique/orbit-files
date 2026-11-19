package com.orbitfiles.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.BuildConfig
import com.orbitfiles.app.data.Accent
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.data.ThemeMode
import com.orbitfiles.app.data.TrashManager
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.ConfirmDialog
import com.orbitfiles.app.ui.components.EmptyState
import com.orbitfiles.app.ui.components.FileRow
import com.orbitfiles.app.ui.components.GroupCard
import com.orbitfiles.app.ui.components.ListRow
import com.orbitfiles.app.ui.components.OrbitScreen
import com.orbitfiles.app.ui.components.SectionHeader
import com.orbitfiles.app.ui.components.SwitchRow
import com.orbitfiles.app.ui.components.bouncyClick
import com.orbitfiles.app.util.Biometrics
import com.orbitfiles.app.util.Intents
import com.orbitfiles.core.FileCategory
import com.orbitfiles.core.Formatters
import com.orbitfiles.core.JournalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ------------------------------------------------------------------ Trash

@Composable
fun TrashScreen(c: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsState()
    val version by c.ops.version.collectAsState()
    var items by remember { mutableStateOf<List<TrashManager.Item>?>(null) }
    var emptyAsk by remember { mutableStateOf(false) }
    LaunchedEffect(version) { items = withContext(Dispatchers.IO) { runCatching { c.trash.items() }.getOrDefault(emptyList()) } }
    val total = items?.sumOf { it.entry.size } ?: 0L

    fun act(block: () -> Unit, msg: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching(block).isSuccess }
            c.ops.emit(if (ok) msg else "Não foi possível concluir", error = !ok)
            c.ops.notifyChanged()
        }
    }

    OrbitScreen(
        title = "Lixeira",
        subtitle = items?.let { "${it.size} itens · ${Formatters.size(total)} · removidos após ${settings.trashRetentionDays} dias" },
        onBack = { nav.pop() },
        actions = { if (!items.isNullOrEmpty()) TextButton(onClick = { emptyAsk = true }) { Text("Esvaziar") } },
    ) { padding ->
        val list = items
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.padding(padding)) { EmptyState(Icons.Rounded.DeleteSweep, "Lixeira vazia", "Itens excluídos ficam aqui por ${settings.trashRetentionDays} dias antes de sumirem de vez.") }
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 40.dp)) {
                items(list, key = { it.entry.id }) { item ->
                    val e = item.entry
                    val entry = FileEntry(File(e.originalPath), e.name, e.isDirectory, e.size, e.deletedAt, false,
                        if (e.isDirectory) FileCategory.FOLDER else FileCategory.ofName(e.name), null)
                    FileRow(
                        entry, false, thumbnails = false,
                        onClick = {}, onLongClick = {},
                        modifier = Modifier.animateItem(),
                        subtitle = "${Formatters.size(e.size)} · ${e.daysLeft(settings.trashRetentionDays)} dias restantes · ${File(e.originalPath).parent ?: ""}",
                        trailing = {
                            Row {
                                IconButton(onClick = { act({ item.store.restore(e.id) }, "\"${e.name}\" restaurado") }) { Icon(Icons.Rounded.Restore, "Restaurar", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { act({ item.store.deleteForever(e.id) }, "Excluído permanentemente") }) { Icon(Icons.Rounded.DeleteForever, "Excluir para sempre", tint = MaterialTheme.colorScheme.error) }
                            }
                        },
                    )
                }
            }
        }
    }
    if (emptyAsk) ConfirmDialog(
        "Esvaziar a lixeira?", "${items?.size ?: 0} itens (${Formatters.size(total)}) serão apagados para sempre.", "Esvaziar",
        destructive = true, icon = Icons.Rounded.DeleteForever, onDismiss = { emptyAsk = false },
    ) { emptyAsk = false; act({ c.trash.empty() }, "Lixeira esvaziada") }
}

// ------------------------------------------------------------------ History

@Composable
fun HistoryScreen(c: AppContainer, nav: Navigator) {
    val history by c.ops.history.collectAsState()
    LaunchedEffect(Unit) { c.ops.notifyChanged() }
    OrbitScreen("Histórico", subtitle = "Ações desta sessão — toque em desfazer para reverter", onBack = { nav.pop() }) { padding ->
        if (history.isEmpty()) Box(Modifier.padding(padding)) { EmptyState(Icons.Rounded.History, "Nada por enquanto", "Copiar, mover, renomear e excluir aparecem aqui e podem ser desfeitos.") }
        else LazyColumn(contentPadding = padding) {
            items(history) { e ->
                ListRow(
                    e.title, Formatters.relative(e.time), Icons.Rounded.History,
                    trailing = if (e.undoable) ({ TextButton(onClick = { c.ops.undo(e) }) { Icon(Icons.AutoMirrored.Rounded.Undo, null); Spacer(Modifier.width(4.dp)); Text("Desfazer") } }) else null,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Wi-Fi share

@Composable
fun ShareScreen(c: AppContainer, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by c.lan.state.collectAsState()
    val settings by c.settings.settings.collectAsState()
    OrbitScreen("Wi-Fi", subtitle = "Acesse os arquivos do celular pelo navegador de qualquer PC", onBack = { nav.pop() }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (state.running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(88.dp).background(
                                if (state.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(if (state.running) Icons.Rounded.Wifi else Icons.Rounded.WifiOff, null, Modifier.size(42.dp),
                                tint = if (state.running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(if (state.running) "Compartilhamento ativo" else "Compartilhamento desligado", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        if (state.running && state.url != null) {
                            Text("Abra no navegador do computador (mesma rede):", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                                SelectionContainer {
                                    Text(state.url!!, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { Intents.copyText(context, "Link", state.url!!) }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copiar") }
                                OutlinedButton(onClick = { Intents.shareText(context, state.url!!) }) { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Enviar") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("O link contém uma chave de acesso única. Quem não tiver o link não entra.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        } else {
                            Text(state.error ?: "Conecte o celular e o PC à mesma rede Wi-Fi e toque em Iniciar.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (state.running) c.lan.stop()
                                else scope.launch(Dispatchers.IO) { c.lan.start(c.storage.primaryRoot, settings.lanUpload) }
                            },
                            shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().height(54.dp),
                        ) { Text(if (state.running) "Parar" else "Iniciar") }
                    }
                }
            }
            item {
                GroupCard(Modifier.padding(horizontal = 16.dp)) {
                    SwitchRow("Permitir envio do PC para o celular", "Arquivos enviados vão para a pasta aberta no navegador", Icons.Rounded.UploadFile, settings.lanUpload, enabled = !state.running) {
                        scope.launch { c.settings.setLanUpload(it) }
                    }
                }
            }
            if (state.log.isNotEmpty()) {
                item { SectionHeader("Atividade") }
                items(state.log) { line -> Text(line, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

// ------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(c: AppContainer, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsState()
    fun save(block: suspend () -> Unit) { scope.launch { block() } }
    OrbitScreen("Configurações", onBack = { nav.pop() }) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 48.dp)) {
            item { SectionHeader("Aparência") }
            item {
                GroupCard(Modifier.padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Brightness6, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Tema", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = s.themeMode == m,
                                    onClick = { save { c.settings.setThemeMode(m) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                                ) { Text(m.label) }
                            }
                        }
                    }
                    SwitchRow("Preto AMOLED", "Fundo totalmente preto no tema escuro (economiza bateria)", Icons.Rounded.DarkMode, s.amoled) { save { c.settings.setAmoled(it) } }
                    SwitchRow("Cores do papel de parede", "Material You dinâmico (Android 12+)", Icons.Rounded.Wallpaper, s.dynamicColor,
                        enabled = android.os.Build.VERSION.SDK_INT >= 31) { save { c.settings.setDynamicColor(it) } }
                    if (!s.dynamicColor || android.os.Build.VERSION.SDK_INT < 31) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text("Cor de destaque", style = MaterialTheme.typography.bodyLarge)
                            }
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Accent.entries.forEach { a ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(48.dp).clip(RoundedCornerShape(if (s.accent == a) 24.dp else 16.dp)).background(Color(a.seed))
                                                .bouncyClick { save { c.settings.setAccent(a) } },
                                            contentAlignment = Alignment.Center,
                                        ) { if (s.accent == a) Icon(Icons.Rounded.Check, null, tint = Color.White) }
                                        Text(a.label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    SwitchRow("Modo compacto", "Mais itens por tela", Icons.Rounded.GridView, s.compact) { save { c.settings.setCompact(it) } }
                    SwitchRow("Reduzir animações", "Transições rápidas e sem efeito elástico", Icons.Rounded.Animation, s.reduceMotion) { save { c.settings.setReduceMotion(it) } }
                }
            }
            item { SectionHeader("Navegação") }
            item {
                GroupCard(Modifier.padding(horizontal = 16.dp)) {
                    SwitchRow("Mostrar arquivos ocultos", "Itens que começam com ponto", Icons.Rounded.Visibility, s.showHidden) { save { c.settings.setShowHidden(it) } }
                    SwitchRow("Pastas primeiro", null, Icons.Rounded.FolderSpecial, s.foldersFirst) { save { c.settings.setFoldersFirst(it) } }
                    SwitchRow("Lembrar visualização por pasta", "Cada pasta guarda sua ordenação e modo lista/grade", Icons.Rounded.GridView, s.rememberFolderView) { save { c.settings.setRememberFolderView(it) } }
                    SwitchRow("Miniaturas", "Prévias de fotos, vídeos e ícones de APK", Icons.Rounded.Image, s.thumbnails) { save { c.settings.setThumbnails(it) } }
                    SwitchRow("Vibração", "Resposta tátil em toques longos", Icons.Rounded.Vibration, s.haptics) { save { c.settings.setHaptics(it) } }
                }
            }
            item { SectionHeader("Exclusão") }
            item {
                GroupCard(Modifier.padding(horizontal = 16.dp)) {
                    SwitchRow("Usar lixeira", "Itens excluídos podem ser restaurados", Icons.Rounded.Delete, s.useTrash) { save { c.settings.setUseTrash(it) } }
                    SwitchRow("Confirmar antes de excluir", null, Icons.Rounded.Info, s.confirmDelete) { save { c.settings.setConfirmDelete(it) } }
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text("Manter itens na lixeira por", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 15, 30, 60).forEach { d ->
                                FilterChip(s.trashRetentionDays == d, { save { c.settings.setRetention(d) } }, label = { Text("$d dias") })
                            }
                        }
                    }
                }
            }
            item { SectionHeader("Segurança") }
            item {
                GroupCard(Modifier.padding(horizontal = 16.dp)) {
                    val activity = context.findActivity()
                    val canLock = activity != null && Biometrics.canUseDeviceAuth(activity)
                    SwitchRow("Bloquear o app", if (canLock) "Pede digital, rosto ou bloqueio de tela ao abrir" else "Configure um bloqueio de tela no Android para usar",
                        Icons.Rounded.Fingerprint, s.appLock, enabled = canLock) { v ->
                        if (activity != null) Biometrics.authenticateDevice(activity, if (v) "Ativar bloqueio" else "Desativar bloqueio", onSuccess = { save { c.settings.setAppLock(v) } })
                    }
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Bloquear o Cofre ao sair do app após", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Na hora", 60 to "1 min", 300 to "5 min", 900 to "15 min").forEach { (sec, label) ->
                                FilterChip(s.vaultAutoLockSeconds == sec, { save { c.settings.setVaultAutoLock(sec) } }, label = { Text(label) })
                            }
                        }
                    }
                    ListRow("Abrir o Cofre", null, Icons.Rounded.Lock, onClick = { nav.push(com.orbitfiles.app.ui.Dest.Vault) })
                }
            }
            item { SectionHeader("Sobre") }
            item {
                GroupCard(Modifier.padding(horizontal = 16.dp)) {
                    ListRow("Orbit Files ${BuildConfig.VERSION_NAME}", "Gerenciador de arquivos moderno, sem anúncios e sem rastreamento. Nada sai do aparelho.", Icons.Rounded.Info)
                    ListRow("Permissão de acesso a arquivos", "Abrir configurações do sistema", Icons.Rounded.FolderSpecial, onClick = {
                        runCatching { context.startActivity(Intents.allFilesAccessIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                    ListRow("Detalhes do app no Android", null, Icons.Rounded.Info, onClick = {
                        runCatching {
                            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    })
                }
            }
        }
    }
}
