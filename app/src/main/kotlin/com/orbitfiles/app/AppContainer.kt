package com.orbitfiles.app

import android.content.Context
import com.orbitfiles.app.data.SettingsRepository
import com.orbitfiles.app.data.StorageRepository
import com.orbitfiles.app.data.TrashManager
import com.orbitfiles.app.ops.OperationManager
import com.orbitfiles.app.share.LanShareController
import com.orbitfiles.app.vault.VaultManager
import com.orbitfiles.core.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-written dependency graph: no reflection, no annotation processing, instant startup. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context, appScope)
    val storage = StorageRepository(context)
    val trash = TrashManager(storage)
    val journal = Journal({ file -> runCatching { trash.storeFor(file) }.getOrNull() })
    val ops = OperationManager(context, appScope, trash, journal, settings.settings)
    val vault = VaultManager(context)
    val lan = LanShareController()
}
