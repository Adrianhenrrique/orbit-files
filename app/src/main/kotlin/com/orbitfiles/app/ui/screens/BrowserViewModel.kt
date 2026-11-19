package com.orbitfiles.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.data.FileEntry
import com.orbitfiles.app.data.FolderPref
import com.orbitfiles.app.data.ViewMode
import com.orbitfiles.core.SortBy
import com.orbitfiles.core.TextMatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

/** One browsing location (a tab or a pane) with its own history and selection. */
class PaneState(initial: File, val id: Long) {
    var dir by mutableStateOf(initial)
    var entries by mutableStateOf<List<FileEntry>>(emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var selection by mutableStateOf<Set<String>>(emptySet())
    var filter by mutableStateOf("")
    var refreshing by mutableStateOf(false)
    val history = ArrayDeque<File>()
    /** Restored scroll positions per folder (index, offset). */
    val scroll = HashMap<String, Pair<Int, Int>>()
    var job: Job? = null

    val visible: List<FileEntry> get() = if (filter.isBlank()) entries else entries.filter { TextMatch.score(it.name, filter) > 0 }
    val selectionMode: Boolean get() = selection.isNotEmpty()
    fun selectedFiles(): List<File> = entries.filter { it.path in selection }.map { it.file }
}

class BrowserViewModel(private val c: AppContainer) : ViewModel() {
    private var nextId = 1L
    val tabs = mutableStateListOf<PaneState>()
    var active by mutableIntStateOf(0)
    /** Index of the tab shown in the second pane on wide screens. */
    var secondary by mutableIntStateOf(-1)

    val settings get() = c.settings.settings.value

    init {
        val primary = c.storage.primaryRoot
        tabs += PaneState(primary, nextId++).also { load(it) }
        // Reload every open folder whenever something changes on disk through the app.
        viewModelScope.launch { c.ops.version.drop(1).collect { _ -> tabs.forEach { pane -> load(pane, quiet = true) } } }
        viewModelScope.launch { c.settings.settings.drop(1).collect { _ -> tabs.forEach { pane -> load(pane, quiet = true) } } }
    }

    val current: PaneState get() = tabs[active.coerceIn(0, tabs.lastIndex)]

    fun pane(index: Int): PaneState? = tabs.getOrNull(index)

    fun prefFor(dir: File): FolderPref {
        val s = settings
        return (if (s.rememberFolderView) s.folderPrefs[dir.absolutePath] else null)
            ?: FolderPref(s.sortBy, s.ascending, s.viewMode)
    }

    fun load(p: PaneState, quiet: Boolean = false) {
        p.job?.cancel()
        if (!quiet) p.loading = true
        p.job = viewModelScope.launch {
            try {
                val pref = prefFor(p.dir)
                val list = c.storage.list(p.dir, settings.showHidden, pref.sortBy, pref.ascending, settings.foldersFirst)
                p.entries = list
                p.error = null
                val alive = list.map { it.path }.toSet()
                if (p.selection.isNotEmpty()) p.selection = p.selection.filter { it in alive }.toSet()
            } catch (e: Exception) {
                p.entries = emptyList()
                p.error = e.message ?: "Não foi possível abrir esta pasta"
            } finally {
                p.loading = false
                p.refreshing = false
            }
        }
    }

    fun refresh(p: PaneState) { p.refreshing = true; load(p, quiet = true) }

    fun navigate(p: PaneState, dir: File, pushHistory: Boolean = true) {
        if (dir.absolutePath == p.dir.absolutePath) return
        if (pushHistory) p.history.addLast(p.dir)
        if (p.history.size > 100) p.history.removeFirst()
        p.dir = dir
        p.selection = emptySet()
        p.filter = ""
        load(p)
    }

    /** Goes to the parent folder (bounded by the storage volume root). Returns false at the root. */
    fun up(p: PaneState): Boolean {
        val vol = c.storage.volumeFor(p.dir)
        val parent = p.dir.parentFile ?: return false
        if (vol != null && p.dir.absolutePath == vol.root.path) return false
        navigate(p, parent)
        return true
    }

    fun canGoUp(p: PaneState): Boolean {
        val vol = c.storage.volumeFor(p.dir) ?: return p.dir.parentFile != null
        return p.dir.absolutePath != vol.root.path
    }

    fun toggleSelect(p: PaneState, e: FileEntry) {
        p.selection = if (e.path in p.selection) p.selection - e.path else p.selection + e.path
    }

    fun selectAll(p: PaneState) { p.selection = p.visible.map { it.path }.toSet() }
    fun invertSelection(p: PaneState) { p.selection = p.visible.map { it.path }.toSet() - p.selection }
    fun clearSelection(p: PaneState) { p.selection = emptySet() }

    fun newTab(dir: File = current.dir) {
        tabs += PaneState(dir, nextId++).also { load(it) }
        active = tabs.lastIndex
    }

    fun closeTab(index: Int) {
        if (tabs.size <= 1) return
        tabs.removeAt(index)
        if (secondary >= tabs.size || secondary == index) secondary = -1
        active = active.coerceAtMost(tabs.lastIndex)
    }

    /** Ensures there is a second pane for dual-pane layouts (shares tabs list). */
    fun ensureSecondary() {
        if (secondary in tabs.indices && secondary != active) return
        val other = tabs.indices.firstOrNull { it != active }
        if (other != null) secondary = other
        else {
            tabs += PaneState(current.dir, nextId++).also { load(it) }
            secondary = tabs.lastIndex
        }
    }

    fun setView(p: PaneState, sortBy: SortBy? = null, ascending: Boolean? = null, view: ViewMode? = null) {
        val old = prefFor(p.dir)
        val pref = FolderPref(sortBy ?: old.sortBy, ascending ?: old.ascending, view ?: old.viewMode)
        viewModelScope.launch {
            if (settings.rememberFolderView) c.settings.setFolderPref(p.dir.absolutePath, pref)
            else c.settings.setDefaultView(pref.sortBy, pref.ascending, pref.viewMode)
            load(p, quiet = true)
        }
    }

    fun toggleHidden() = viewModelScope.launch { c.settings.setShowHidden(!settings.showHidden) }

    fun toggleBookmark(dir: File) = viewModelScope.launch { c.settings.toggleBookmark(dir.absolutePath) }

    fun openRequested(dir: File) {
        val p = current
        navigate(p, dir)
    }
}
