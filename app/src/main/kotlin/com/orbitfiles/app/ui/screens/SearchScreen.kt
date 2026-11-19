package com.orbitfiles.app.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.ui.FileOpener
import com.orbitfiles.app.ui.Navigator
import com.orbitfiles.app.ui.components.EmptyState
import com.orbitfiles.app.ui.components.FileRow
import com.orbitfiles.core.CancelToken
import com.orbitfiles.core.FileSearcher
import com.orbitfiles.core.NaturalQueryParser
import com.orbitfiles.core.OperationCancelledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    var text by mutableStateOf("")
    var chips by mutableStateOf<List<String>>(emptyList())
        private set
    var results by mutableStateOf<List<FileEntry>>(emptyList())
        private set
    var contentLines by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var searching by mutableStateOf(false)
        private set
    var searched by mutableStateOf(false)
        private set
    var searchContent by mutableStateOf(false)
    var includeHidden by mutableStateOf(false)
    var regex by mutableStateOf(false)
    val history = mutableStateListOf<String>()

    private val parser = NaturalQueryParser()
    private var job: Job? = null
    private var token: CancelToken? = null

    /** Debounced live search while typing. */
    fun onTextChange(v: String) {
        text = v
        chips = if (v.isBlank()) emptyList() else parser.parse(v).chips
        schedule(350)
    }

    fun schedule(delayMs: Long = 0) {
        job?.cancel(); token?.cancel()
        if (text.isBlank()) { results = emptyList(); searched = false; searching = false; return }
        job = viewModelScope.launch {
            delay(delayMs)
            run()
        }
    }

    private suspend fun run() {
        val parsed = parser.parse(text)
        val q = if (regex) parsed.query.copy(text = text.trim(), regex = true, categories = emptySet(), minSize = null, maxSize = null, modifiedAfter = null, modifiedBefore = null, extensions = emptySet())
        else parsed.query
        val query = q.copy(searchContent = searchContent, includeHidden = includeHidden || q.includeHidden, limit = 1500)
        val t = CancelToken().also { token = it }
        searching = true
        results = emptyList(); contentLines = emptyMap()
        val roots = c.storage.volumes().map { it.root }
        try {
            val hits = withContext(Dispatchers.IO) {
                FileSearcher().search(roots, query, t) { batch ->
                    val add = batch.map { FileEntry.of(it.file, countChildren = false) }
                    viewModelScope.launch { if (token === t) results = (results + add).distinctBy { it.path }.take(1500) }
                }
            }
            results = hits.map { FileEntry.of(it.file, countChildren = false) }.distinctBy { it.path }
            contentLines = hits.mapNotNull { h -> h.contentLine?.let { h.file.absolutePath to it } }.toMap()
            searched = true
            val trimmed = text.trim()
            if (trimmed.isNotBlank()) { history.remove(trimmed); history.add(0, trimmed); while (history.size > 8) history.removeAt(history.lastIndex) }
        } catch (_: OperationCancelledException) {
        } finally {
            searching = false
        }
    }

    fun clear() { job?.cancel(); token?.cancel(); text = ""; chips = emptyList(); results = emptyList(); searched = false; searching = false }
}

private val suggestions = listOf(
    "fotos de ontem", "vídeos grandes", "pdf maior que 5 mb", "documentos da semana passada",
    "apk", "músicas do mês passado", "zip antigos", "capturas recentes",
)

@Composable
fun SearchScreen(c: AppContainer, vm: SearchViewModel, nav: Navigator) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val settings by c.settings.settings.collectAsState()
    LaunchedEffect(nav.searchRequest) {
        nav.searchRequest?.let { vm.onTextChange(it); vm.schedule(); nav.searchRequest = null }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Buscar", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp))
        OutlinedTextField(
            value = vm.text,
            onValueChange = vm::onTextChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Ex.: fotos de ontem, pdf > 10 mb, ext:kt") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (vm.text.isNotEmpty()) IconButton(onClick = vm::clear) { Icon(Icons.Rounded.Close, "Limpar") } },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); vm.schedule() }),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(vm.searchContent, { vm.searchContent = !vm.searchContent; vm.schedule() }, label = { Text("Dentro de textos") }) }
            item { FilterChip(vm.includeHidden, { vm.includeHidden = !vm.includeHidden; vm.schedule() }, label = { Text("Ocultos") }) }
            item { FilterChip(vm.regex, { vm.regex = !vm.regex; vm.schedule() }, label = { Text("Regex") }) }
        }
        if (vm.chips.isNotEmpty() && !vm.regex) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text("  Entendi: " + vm.chips.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
        else Spacer(Modifier.height(4.dp))
        Box(Modifier.weight(1f)) {
            when {
                vm.text.isBlank() -> Column(Modifier.padding(16.dp)) {
                    if (vm.history.isNotEmpty()) {
                        Text("Buscas recentes", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.history.forEach { h ->
                                AssistChip(onClick = { vm.onTextChange(h); vm.schedule() }, label = { Text(h) }, leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) })
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Text("Experimente perguntar", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s -> SuggestionChip(onClick = { vm.onTextChange(s); vm.schedule() }, label = { Text(s) }) }
                    }
                }
                vm.searched && vm.results.isEmpty() && !vm.searching ->
                    EmptyState(Icons.Rounded.SearchOff, "Nada encontrado", "Tente outros termos ou ative \"Dentro de textos\".")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    item {
                        if (vm.results.isNotEmpty()) Text(
                            "${vm.results.size} resultados", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    items(vm.results, key = { it.path }) { e ->
                        val line = vm.contentLines[e.path]
                        FileRow(
                            e, false, settings.thumbnails,
                            onClick = { if (e.isDirectory) nav.openFolder(e.file) else FileOpener.open(context, nav, e.file) },
                            onLongClick = { e.file.parentFile?.let { nav.openFolder(it) } },
                            modifier = Modifier.animateItem(),
                            subtitle = line?.let { "“$it”" } ?: c.storage.displayPath(e.file.parentFile ?: e.file),
                            trailing = {
                                IconButton(onClick = { e.file.parentFile?.let { nav.openFolder(it) } }) {
                                    Icon(Icons.Rounded.FolderOpen, "Abrir local", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
