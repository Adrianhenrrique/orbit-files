package com.orbitfiles.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orbitfiles.core.SortBy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro") }
enum class ViewMode { LIST, GRID }

/** Brand palettes used when dynamic color is off (or unavailable on Android < 12). */
enum class Accent(val label: String, val seed: Long) {
    ORBIT("Órbita", 0xFF8B7BFF),
    AURORA("Aurora", 0xFF2FD3A6),
    OCEAN("Oceano", 0xFF3D8BFF),
    SOLAR("Solar", 0xFFFF8A3D),
    ROSE("Rosa", 0xFFFF5C93),
    LIME("Lima", 0xFF9BD43A),
}

data class FolderPref(val sortBy: SortBy, val ascending: Boolean, val viewMode: ViewMode)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val accent: Accent = Accent.ORBIT,
    val showHidden: Boolean = false,
    val foldersFirst: Boolean = true,
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST,
    val useTrash: Boolean = true,
    val trashRetentionDays: Int = 30,
    val confirmDelete: Boolean = true,
    val appLock: Boolean = false,
    val vaultBiometric: Boolean = false,
    val vaultAutoLockSeconds: Int = 60,
    val thumbnails: Boolean = true,
    val reduceMotion: Boolean = false,
    val compact: Boolean = false,
    val haptics: Boolean = true,
    val lanUpload: Boolean = true,
    val rememberFolderView: Boolean = true,
    val onboarded: Boolean = false,
    val bookmarks: List<String> = emptyList(),
    val folderPrefs: Map<String, FolderPref> = emptyMap(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "orbit_settings")

class SettingsRepository(private val context: Context, scope: CoroutineScope) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val amoled = booleanPreferencesKey("amoled")
        val dynamic = booleanPreferencesKey("dynamic")
        val accent = stringPreferencesKey("accent")
        val hidden = booleanPreferencesKey("hidden")
        val foldersFirst = booleanPreferencesKey("folders_first")
        val sortBy = stringPreferencesKey("sort_by")
        val asc = booleanPreferencesKey("asc")
        val view = stringPreferencesKey("view")
        val trash = booleanPreferencesKey("trash")
        val retention = intPreferencesKey("retention")
        val confirm = booleanPreferencesKey("confirm")
        val appLock = booleanPreferencesKey("app_lock")
        val vaultBio = booleanPreferencesKey("vault_bio")
        val vaultLock = intPreferencesKey("vault_lock")
        val thumbs = booleanPreferencesKey("thumbs")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val compact = booleanPreferencesKey("compact")
        val haptics = booleanPreferencesKey("haptics")
        val lanUpload = booleanPreferencesKey("lan_upload")
        val rememberView = booleanPreferencesKey("remember_view")
        val onboarded = booleanPreferencesKey("onboarded")
        val bookmarks = stringPreferencesKey("bookmarks")
        val folderPrefs = stringSetPreferencesKey("folder_prefs")
    }

    private val _loaded = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** True once the persisted settings were read (avoids flashing defaults, e.g. app lock). */
    val loaded: StateFlow<Boolean> = _loaded

    val settings: StateFlow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = enumOr(p[K.theme], ThemeMode.SYSTEM),
            amoled = p[K.amoled] ?: false,
            dynamicColor = p[K.dynamic] ?: true,
            accent = enumOr(p[K.accent], Accent.ORBIT),
            showHidden = p[K.hidden] ?: false,
            foldersFirst = p[K.foldersFirst] ?: true,
            sortBy = enumOr(p[K.sortBy], SortBy.NAME),
            ascending = p[K.asc] ?: true,
            viewMode = enumOr(p[K.view], ViewMode.LIST),
            useTrash = p[K.trash] ?: true,
            trashRetentionDays = p[K.retention] ?: 30,
            confirmDelete = p[K.confirm] ?: true,
            appLock = p[K.appLock] ?: false,
            vaultBiometric = p[K.vaultBio] ?: false,
            vaultAutoLockSeconds = p[K.vaultLock] ?: 60,
            thumbnails = p[K.thumbs] ?: true,
            reduceMotion = p[K.reduceMotion] ?: false,
            compact = p[K.compact] ?: false,
            haptics = p[K.haptics] ?: true,
            lanUpload = p[K.lanUpload] ?: true,
            rememberFolderView = p[K.rememberView] ?: true,
            onboarded = p[K.onboarded] ?: false,
            bookmarks = p[K.bookmarks]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
            folderPrefs = p[K.folderPrefs].orEmpty().mapNotNull(::decodeFolderPref).toMap(),
        ).also { _loaded.value = true }
    }.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private fun decodeFolderPref(s: String): Pair<String, FolderPref>? {
        val parts = s.split('\u0001')
        if (parts.size != 4) return null
        val sort = SortBy.entries.firstOrNull { it.name == parts[1] } ?: return null
        val view = ViewMode.entries.firstOrNull { it.name == parts[3] } ?: return null
        return parts[0] to FolderPref(sort, parts[2] == "1", view)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setThemeMode(v: ThemeMode) = edit { it[K.theme] = v.name }
    suspend fun setAmoled(v: Boolean) = edit { it[K.amoled] = v }
    suspend fun setDynamicColor(v: Boolean) = edit { it[K.dynamic] = v }
    suspend fun setAccent(v: Accent) = edit { it[K.accent] = v.name }
    suspend fun setShowHidden(v: Boolean) = edit { it[K.hidden] = v }
    suspend fun setFoldersFirst(v: Boolean) = edit { it[K.foldersFirst] = v }
    suspend fun setUseTrash(v: Boolean) = edit { it[K.trash] = v }
    suspend fun setRetention(v: Int) = edit { it[K.retention] = v }
    suspend fun setConfirmDelete(v: Boolean) = edit { it[K.confirm] = v }
    suspend fun setAppLock(v: Boolean) = edit { it[K.appLock] = v }
    suspend fun setVaultBiometric(v: Boolean) = edit { it[K.vaultBio] = v }
    suspend fun setVaultAutoLock(v: Int) = edit { it[K.vaultLock] = v }
    suspend fun setThumbnails(v: Boolean) = edit { it[K.thumbs] = v }
    suspend fun setReduceMotion(v: Boolean) = edit { it[K.reduceMotion] = v }
    suspend fun setCompact(v: Boolean) = edit { it[K.compact] = v }
    suspend fun setHaptics(v: Boolean) = edit { it[K.haptics] = v }
    suspend fun setLanUpload(v: Boolean) = edit { it[K.lanUpload] = v }
    suspend fun setRememberFolderView(v: Boolean) = edit { it[K.rememberView] = v }
    suspend fun setOnboarded(v: Boolean) = edit { it[K.onboarded] = v }

    /** Global default view (used for folders without a remembered preference). */
    suspend fun setDefaultView(sortBy: SortBy, ascending: Boolean, view: ViewMode) = edit {
        it[K.sortBy] = sortBy.name; it[K.asc] = ascending; it[K.view] = view.name
    }

    suspend fun setFolderPref(path: String, pref: FolderPref) = edit { p ->
        val current = p[K.folderPrefs].orEmpty().filterNot { it.startsWith(path + '\u0001') }.toMutableSet()
        current += listOf(path, pref.sortBy.name, if (pref.ascending) "1" else "0", pref.viewMode.name).joinToString("\u0001")
        // Keep the store small: at most 300 remembered folders.
        p[K.folderPrefs] = current.toList().takeLast(300).toSet()
    }

    suspend fun toggleBookmark(path: String) = edit { p ->
        val list = p[K.bookmarks]?.split('\n')?.filter { it.isNotBlank() }.orEmpty().toMutableList()
        if (path in list) list.remove(path) else list.add(path)
        p[K.bookmarks] = list.joinToString("\n")
    }
}
