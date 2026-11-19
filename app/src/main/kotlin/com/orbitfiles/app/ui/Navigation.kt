package com.orbitfiles.app.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.orbitfiles.app.data.SmartCollection
import java.io.File

enum class Tab(val label: String) { HOME("Início"), FILES("Arquivos"), SEARCH("Buscar"), TOOLS("Ferramentas") }

/** Screens pushed on top of the tab content. */
sealed interface Dest {
    data class CollectionList(val collection: SmartCollection) : Dest
    data class Analyzer(val root: File) : Dest
    data object Cleaner : Dest
    data object Vault : Dest
    data object Trash : Dest
    data object Settings : Dest
    data object Share : Dest
    data object History : Dest
    data class ImageViewer(val file: File, val siblings: List<File>) : Dest
    data class TextEditor(val file: File, val readOnly: Boolean = false) : Dest
    data class MediaPlayer(val file: File, val playlist: List<File>) : Dest
    data class PdfViewer(val file: File) : Dest
    data class ArchiveViewer(val file: File) : Dest
}

/** Tiny navigation controller: one tab + a back stack of overlay destinations. */
class Navigator : ViewModel() {
    var tab by mutableStateOf(Tab.HOME)
    val stack = mutableStateListOf<Dest>()

    /** Folder to open in the Files tab (consumed by the browser). */
    var openFolderRequest by mutableStateOf<File?>(null)
    var searchRequest by mutableStateOf<String?>(null)

    val current: Dest? get() = stack.lastOrNull()

    fun push(d: Dest) { stack.add(d) }

    fun pop(): Boolean = if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); true } else false

    fun selectTab(t: Tab) { stack.clear(); tab = t }

    fun openFolder(f: File) { stack.clear(); openFolderRequest = f; tab = Tab.FILES }

    fun search(query: String) { stack.clear(); searchRequest = query; tab = Tab.SEARCH }
}

/** False for screens covered by another overlay: their BackHandlers must stay disabled. */
val LocalIsTopScreen = androidx.compose.runtime.compositionLocalOf { true }
