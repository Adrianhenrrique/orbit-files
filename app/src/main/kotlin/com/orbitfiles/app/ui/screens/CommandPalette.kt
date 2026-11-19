package com.orbitfiles.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.ThemeMode
import com.orbitfiles.app.ui.Dest
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.ListRow
import com.orbitfiles.core.CancelToken
import com.orbitfiles.core.FileSearcher
import com.orbitfiles.core.SearchQuery
import com.orbitfiles.core.TextMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class Command(val title: String, val subtitle: String?, val icon: ImageVector, val keywords: String, val run: () -> Unit)

/**
 * Spotlight-style command palette: jump to folders and run any action by typing.
 * Folder results come from a fast, shallow name search.
 */
@Composable
fun CommandPalette(c: AppContainer, nav: Navigator, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<File>>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun go(block: () -> Unit) { onDismiss(); block() }
    val commands = remember {
        buildList {
            c.storage.quickFolders().forEach { (label, dir) ->
                add(Command("Abrir $label", c.storage.displayPath(dir), Icons.Rounded.Folder, "pasta ir abrir $label") { go { nav.openFolder(dir) } })
            }
            add(Command("Armazenamento interno", null, Icons.Rounded.Smartphone, "raiz interno storage") { go { nav.openFolder(c.storage.primaryRoot) } })
            add(Command("Analisar armazenamento", "Mapa de pastas e maiores arquivos", Icons.Rounded.PieChart, "analise espaco storage grafico") { go { nav.push(Dest.Analyzer(c.storage.primaryRoot)) } })
            add(Command("Limpeza inteligente", "Duplicados, lixo e downloads antigos", Icons.Rounded.CleaningServices, "limpar limpeza clean lixo duplicados liberar espaco") { go { nav.push(Dest.Cleaner) } })
            add(Command("Cofre", "Arquivos criptografados", Icons.Rounded.Lock, "cofre vault seguro privado senha") { go { nav.push(Dest.Vault) } })
            add(Command("Lixeira", null, Icons.Rounded.Delete, "lixeira trash restaurar excluidos") { go { nav.push(Dest.Trash) } })
            add(Command("Compartilhar por Wi-Fi", "Acessar pelo navegador do PC", Icons.Rounded.Wifi, "wifi pc computador navegador transferir lan") { go { nav.push(Dest.Share) } })
            add(Command("Histórico e desfazer", null, Icons.Rounded.History, "historico desfazer undo") { go { nav.push(Dest.History) } })
            add(Command("Configurações", null, Icons.Rounded.Settings, "configuracoes ajustes settings preferencias") { go { nav.push(Dest.Settings) } })
            add(Command("Alternar tema claro/escuro", null, Icons.Rounded.DarkMode, "tema escuro claro dark light") {
                go { scope.launch { c.settings.setThemeMode(if (c.settings.settings.value.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK) } }
            })
            add(Command("Mostrar/ocultar arquivos ocultos", null, Icons.Rounded.Visibility, "ocultos hidden mostrar") {
                go { scope.launch { c.settings.setShowHidden(!c.settings.settings.value.showHidden) } }
            })
        }
    }

    LaunchedEffect(query) {
        folders = emptyList()
        if (query.trim().length < 2) return@LaunchedEffect
        delay(200)
        val token = CancelToken()
        try {
            folders = withContext(Dispatchers.IO) {
                FileSearcher().search(listOf(c.storage.primaryRoot), SearchQuery(query.trim(), foldersOnly = true, limit = 12), token).map { it.file }
            }
        } finally { token.cancel() }
    }

    val matched = if (query.isBlank()) commands else commands
        .map { it to maxOf(TextMatch.score(it.title, query), TextMatch.score(it.keywords, query)) }
        .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().statusBarsPadding().imePadding().padding(12.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Digite um comando, pasta ou busca…") },
                    leadingIcon = { Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.primary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) go { nav.search(query) } }),
                    modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),
                    shape = RoundedCornerShape(22.dp),
                )
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    if (query.isNotBlank()) item {
                        ListRow("Buscar “${query.trim()}” em todos os arquivos", "Busca inteligente: entende tipo, tamanho e data", Icons.Rounded.Search, onClick = { go { nav.search(query) } })
                    }
                    items(matched, key = { "cmd:" + it.title }) { cmd -> ListRow(cmd.title, cmd.subtitle, cmd.icon, onClick = cmd.run) }
                    if (folders.isNotEmpty()) {
                        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                        items(folders, key = { "dir:" + it.path }) { f ->
                            ListRow(f.name, c.storage.displayPath(f), Icons.Rounded.Folder, MaterialTheme.colorScheme.tertiary, onClick = { go { nav.openFolder(f) } })
                        }
                    }
                }
            }
        }
    }
}
