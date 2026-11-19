package com.orbitfiles.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import com.orbitfiles.app.ui.OrbitRoot

/**
 * Single activity. Extends FragmentActivity (a ComponentActivity) because BiometricPrompt
 * needs it; the whole UI is Jetpack Compose.
 */
class MainActivity : FragmentActivity() {
    /** Deep link from launcher shortcuts ("search", "cleaner", "vault", "share"). */
    val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingDestination.value = intent?.getStringExtra("destination")
        val container = (application as OrbitApplication).container
        setContent { OrbitRoot(container, this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("destination")?.let { pendingDestination.value = it }
    }
}
