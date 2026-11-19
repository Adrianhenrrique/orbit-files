package com.orbitfiles.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.outlined.Folder as FolderOutlined
import androidx.compose.material.icons.outlined.Home as HomeOutlined
import androidx.compose.material.icons.outlined.Widgets as WidgetsOutlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.orbitfiles.app.AppContainer
import com.orbitfiles.app.MainActivity
import com.orbitfiles.app.OrbitApplication
import com.orbitfiles.app.ui.components.OperationCard
import com.orbitfiles.app.ui.screens.AnalyzerScreen
import com.orbitfiles.app.ui.screens.BrowserScreen
import com.orbitfiles.app.ui.screens.BrowserViewModel
import com.orbitfiles.app.ui.screens.CleanerScreen
import com.orbitfiles.app.ui.screens.CollectionScreen
import com.orbitfiles.app.ui.screens.CommandPalette
import com.orbitfiles.app.ui.screens.HistoryScreen
import com.orbitfiles.app.ui.screens.HomeScreen
import com.orbitfiles.app.ui.screens.HomeViewModel
import com.orbitfiles.app.ui.screens.LockScreen
import com.orbitfiles.app.ui.screens.PermissionScreen
import com.orbitfiles.app.ui.screens.SearchScreen
import com.orbitfiles.app.ui.screens.SearchViewModel
import com.orbitfiles.app.ui.screens.SettingsScreen
import com.orbitfiles.app.ui.screens.ShareScreen
import com.orbitfiles.app.ui.screens.ToolsScreen
import com.orbitfiles.app.ui.screens.TrashScreen
import com.orbitfiles.app.ui.screens.VaultScreen
import com.orbitfiles.app.ui.theme.OrbitTheme
import com.orbitfiles.app.ui.viewer.ArchiveViewer
import com.orbitfiles.app.ui.viewer.ImageViewer
import com.orbitfiles.app.ui.viewer.MediaPlayerScreen
import com.orbitfiles.app.ui.viewer.PdfViewer
import com.orbitfiles.app.ui.viewer.TextEditor

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

@Composable
fun OrbitRoot(c: AppContainer, activity: MainActivity) {
    val settings by c.settings.settings.collectAsState()
    val loaded by c.settings.loaded.collectAsState()
    OrbitTheme(settings) {
        val haptics = if (settings.haptics) LocalHapticFeedback.current else NoHaptics
        CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (loaded) Gate(c, activity)
            }
        }
    }
}

/** Permission → app lock → main UI. */
@Composable
private fun Gate(c: AppContainer, activity: MainActivity) {
    val app = activity.application as OrbitApplication
    val settings by c.settings.settings.collectAsState()
    var granted by remember { mutableStateOf(c.storage.hasAllFilesAccess()) }
    var sessionUnlocked by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = c.storage.hasAllFilesAccess() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        // Re-lock when the app was in background for more than 30 s.
        if (app.backgroundedAt > 0 && System.currentTimeMillis() - app.backgroundedAt > 30_000) sessionUnlocked = false
    }
    when {
        !granted -> PermissionScreen { granted = c.storage.hasAllFilesAccess() }
        settings.appLock && !sessionUnlocked -> LockScreen { sessionUnlocked = true }
        else -> MainUi(c, activity)
    }
}

private data class TabSpec(val tab: Tab, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    TabSpec(Tab.HOME, Icons.Rounded.Home, Icons.Outlined.HomeOutlined),
    TabSpec(Tab.FILES, Icons.Rounded.Folder, Icons.Outlined.FolderOutlined),
    TabSpec(Tab.SEARCH, Icons.Rounded.Search, Icons.Rounded.Search),
    TabSpec(Tab.TOOLS, Icons.Rounded.Widgets, Icons.Outlined.WidgetsOutlined),
)

@Composable
private fun MainUi(c: AppContainer, activity: MainActivity) {
    val context = LocalContext.current
    val nav: Navigator = viewModel()
    val browserVm = viewModel { BrowserViewModel(c) }
    val homeVm = viewModel { HomeViewModel(c) }
    val searchVm = viewModel { SearchViewModel(c) }
    val tasks by c.ops.tasks.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var palette by remember { mutableStateOf(false) }

    // Ask once for notification permission (progress of long operations).
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val s = c.settings.settings.value
        if (!s.onboarded) {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            c.settings.setOnboarded(true)
        }
    }

    // Launcher shortcuts.
    val pending = activity.pendingDestination.value
    LaunchedEffect(pending) {
        when (pending) {
            "search" -> nav.selectTab(Tab.SEARCH)
            "cleaner" -> { nav.selectTab(Tab.TOOLS); nav.push(Dest.Cleaner) }
            "vault" -> { nav.selectTab(Tab.TOOLS); nav.push(Dest.Vault) }
            "share" -> { nav.selectTab(Tab.TOOLS); nav.push(Dest.Share) }
        }
        activity.pendingDestination.value = null
    }

    // Snackbars with "Desfazer".
    LaunchedEffect(Unit) {
        c.ops.events.collect { ev ->
            snackbar.currentSnackbarData?.dismiss()
            val res = snackbar.showSnackbar(
                message = ev.message,
                actionLabel = if (ev.undo != null) "Desfazer" else null,
                withDismissAction = ev.isError,
                duration = if (ev.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (res == SnackbarResult.ActionPerformed) ev.undo?.let { c.ops.undo(it) }
        }
    }

    // Root back: close overlays, then return to Home.
    BackHandler(enabled = nav.stack.isNotEmpty() || nav.tab != Tab.HOME) {
        if (!nav.pop()) nav.selectTab(Tab.HOME)
    }

    val overlay = nav.stack.isNotEmpty()
    val selecting = nav.tab == Tab.FILES && browserVm.tabs.isNotEmpty() && browserVm.current.selectionMode

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (rail && !overlay) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Spacer(Modifier.height(12.dp))
                    tabs.forEach { t ->
                        NavigationRailItem(
                            selected = nav.tab == t.tab,
                            onClick = { nav.selectTab(t.tab) },
                            icon = { Icon(if (nav.tab == t.tab) t.selected else t.unselected, t.tab.label) },
                            label = { Text(t.tab.label) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                    // Tab content.
                    AnimatedContent(
                        targetState = nav.tab,
                        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.98f)) togetherWith fadeOut() },
                        label = "tab",
                    ) { tab ->
                        CompositionLocalProvider(LocalIsTopScreen provides !overlay) {
                            when (tab) {
                                Tab.HOME -> HomeScreen(c, homeVm, nav, onOpenPalette = { palette = true })
                                Tab.FILES -> BrowserScreen(c, browserVm, nav, overlayVisible = overlay)
                                Tab.SEARCH -> SearchScreen(c, searchVm, nav)
                                Tab.TOOLS -> ToolsScreen(c, nav)
                            }
                        }
                    }
                    // Pushed screens, stacked so lower ones keep their state.
                    nav.stack.forEachIndexed { index, dest ->
                        key(index, dest) {
                            val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                            androidx.compose.animation.AnimatedVisibility(visibleState = visible, enter = slideInHorizontally { it / 4 } + fadeIn()) {
                                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                                    CompositionLocalProvider(LocalIsTopScreen provides (index == nav.stack.lastIndex)) {
                                        DestContent(c, nav, dest)
                                    }
                                }
                            }
                        }
                    }
                    // Running operations + snackbars float above everything.
                    Column(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                            .then(if (rail || overlay || selecting) Modifier.navigationBarsPadding() else Modifier),
                    ) {
                        tasks.takeLast(3).forEach { t ->
                            key(t.id) {
                                OperationCard(t, onCancel = { c.ops.cancel(t.id) }, onDismiss = { c.ops.dismiss(t.id) }, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                        SnackbarHost(snackbar) { data -> Snackbar(data, shape = MaterialTheme.shapes.large) }
                    }
                }
                AnimatedVisibility(!rail && !overlay && !selecting, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = nav.tab == t.tab,
                                onClick = { nav.selectTab(t.tab) },
                                icon = { Icon(if (nav.tab == t.tab) t.selected else t.unselected, t.tab.label) },
                                label = { Text(t.tab.label) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (palette) CommandPalette(c, nav) { palette = false }
}

@Composable
private fun DestContent(c: AppContainer, nav: Navigator, dest: Dest) {
    when (dest) {
        is Dest.CollectionList -> CollectionScreen(c, nav, dest.collection)
        is Dest.Analyzer -> AnalyzerScreen(c, nav, dest.root)
        Dest.Cleaner -> CleanerScreen(c, nav)
        Dest.Vault -> VaultScreen(c, nav)
        Dest.Trash -> TrashScreen(c, nav)
        Dest.Settings -> SettingsScreen(c, nav)
        Dest.Share -> ShareScreen(c, nav)
        Dest.History -> HistoryScreen(c, nav)
        is Dest.ImageViewer -> ImageViewer(nav, dest.file, dest.siblings)
        is Dest.TextEditor -> TextEditor(c, nav, dest.file, dest.readOnly)
        is Dest.MediaPlayer -> MediaPlayerScreen(nav, dest.file, dest.playlist)
        is Dest.PdfViewer -> PdfViewer(nav, dest.file)
        is Dest.ArchiveViewer -> ArchiveViewer(c, nav, dest.file)
    }
}
