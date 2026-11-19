package com.orbitfiles.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import com.orbitfiles.app.ui.theme.LocalReduceMotion
import com.orbitfiles.app.util.Biometrics
import com.orbitfiles.app.util.Intents

/** Animated brand mark: a folder orbited by a satellite. */
@Composable
fun OrbitLogo(size: androidx.compose.ui.unit.Dp = 120.dp) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "orbit")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (reduce) 60000 else 6000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size * 0.62f).background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
                RoundedCornerShape(size * 0.2f),
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(size * 0.32f), tint = MaterialTheme.colorScheme.onPrimary) }
        Box(Modifier.size(size).rotate(angle), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.size(size * 0.14f).background(MaterialTheme.colorScheme.tertiary, CircleShape))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** First run / missing permission: explains why "all files access" is needed. */
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onGranted() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        OrbitLogo()
        Spacer(Modifier.height(24.dp))
        Text("Bem-vindo ao Orbit Files", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Para organizar, copiar e limpar seus arquivos, o Android pede que você libere o acesso a todos os arquivos.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Feature(Icons.Rounded.Bolt, "Rápido e leve", "Operações em segundo plano com progresso, velocidade e desfazer.")
            Feature(Icons.Rounded.Shield, "Privado de verdade", "Sem anúncios, sem rastreadores, sem internet — exceto o Wi-Fi local que você ativar.")
            Feature(Icons.Rounded.Lock, "Cofre criptografado", "AES-256-GCM com senha e biometria.")
            Feature(Icons.Rounded.Wifi, "Do celular para o PC", "Abra seus arquivos pelo navegador, sem cabo.")
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching { context.startActivity(Intents.allFilesAccessIntent(context)) }
                        .onFailure { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                } else {
                    legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large,
        ) { Text("Permitir acesso aos arquivos") }
        Spacer(Modifier.height(10.dp))
        Text(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Na tela seguinte, ative “Permitir acesso para gerenciar todos os arquivos” e volte." else "Toque em Permitir na janela do sistema.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

/** App lock screen shown on launch/resume when enabled. */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    fun prompt() {
        val activity = context.findActivity() ?: return
        Biometrics.authenticateDevice(activity, "Desbloquear Orbit Files", onSuccess = onUnlocked)
    }
    LaunchedEffect(Unit) { prompt() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        OrbitLogo(96.dp)
        Spacer(Modifier.height(24.dp))
        Text("Orbit Files está bloqueado", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = ::prompt, shape = MaterialTheme.shapes.large, modifier = Modifier.height(54.dp)) {
            Icon(Icons.Rounded.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Desbloquear")
        }
    }
}
