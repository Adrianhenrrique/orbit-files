package com.orbitfiles.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OrbitApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    /** Timestamp of the last time the app went to background (for app lock / vault auto-lock). */
    @Volatile var backgroundedAt: Long = 0L
        private set

    private var autoLockJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Housekeeping: purge expired trash entries once per start, off the main thread.
        container.appScope.launch(Dispatchers.IO) {
            delay(3000)
            if (container.storage.hasAllFilesAccess()) {
                runCatching { container.trash.purge(container.settings.settings.value.trashRetentionDays) }
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundedAt = System.currentTimeMillis()
                val seconds = container.settings.settings.value.vaultAutoLockSeconds
                autoLockJob?.cancel()
                autoLockJob = container.appScope.launch {
                    delay(seconds * 1000L)
                    container.vault.lock()
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                autoLockJob?.cancel()
            }
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
