package com.orbitfiles.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbitfiles.app.data.AppSettings
import com.orbitfiles.app.data.ThemeMode
import com.orbitfiles.core.FileCategory

/** True when the user asked for reduced motion: springs become quick, non-bouncy tweens. */
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalCompact = staticCompositionLocalOf { false }
val LocalIsDark = staticCompositionLocalOf { true }

object Motion {
    /** Material 3 Expressive style springs. */
    fun <T> bouncy(reduce: Boolean): SpringSpec<T> =
        if (reduce) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.55f, stiffness = 420f)
    fun <T> smooth(reduce: Boolean): SpringSpec<T> =
        if (reduce) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}

object CategoryColors {
    fun of(cat: FileCategory, primary: Color): Color = when (cat) {
        FileCategory.FOLDER -> primary
        FileCategory.IMAGE -> Color(0xFFFF6FA3)
        FileCategory.VIDEO -> Color(0xFFFF7A59)
        FileCategory.AUDIO -> Color(0xFFFFB547)
        FileCategory.DOCUMENT -> Color(0xFF4C9BFF)
        FileCategory.PDF -> Color(0xFFFF5A5F)
        FileCategory.SPREADSHEET -> Color(0xFF2FBF71)
        FileCategory.PRESENTATION -> Color(0xFFFF9F43)
        FileCategory.ARCHIVE -> Color(0xFFA77BFF)
        FileCategory.APK -> Color(0xFF3DDC84)
        FileCategory.CODE -> Color(0xFF2EC8B0)
        FileCategory.TEXT -> Color(0xFF8FA3BF)
        FileCategory.EBOOK -> Color(0xFFC99A6B)
        FileCategory.FONT -> Color(0xFFE07BE0)
        FileCategory.OTHER -> Color(0xFF9AA0B5)
    }
}

// ---------- palette generation from a seed (lightweight tonal system based on HSL) ----------

private fun hsl(color: Color): FloatArray {
    val r = color.red; val g = color.green; val b = color.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    var h = 0f; var s = 0f
    if (max != min) {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
    }
    return floatArrayOf(h, s, l)
}

private fun tone(hue: Float, sat: Float, light: Float): Color = Color.hsl(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), light.coerceIn(0f, 1f))

private fun schemeFromSeed(seed: Color, dark: Boolean, amoled: Boolean): ColorScheme {
    val (h, s0, _) = hsl(seed).let { Triple(it[0], it[1], it[2]) }
    val s = s0.coerceIn(0.45f, 0.9f)
    val sec = h + 25f
    val ter = h + 160f
    val n = 0.10f // neutral saturation keeps surfaces subtly tinted by the brand hue
    return if (dark) {
        val bg = if (amoled) Color.Black else tone(h, n, 0.065f)
        darkColorScheme(
            primary = tone(h, s, 0.78f), onPrimary = tone(h, s, 0.16f),
            primaryContainer = tone(h, s * 0.75f, 0.30f), onPrimaryContainer = tone(h, s, 0.92f),
            inversePrimary = tone(h, s, 0.42f),
            secondary = tone(sec, s * 0.45f, 0.78f), onSecondary = tone(sec, s * 0.4f, 0.18f),
            secondaryContainer = tone(sec, s * 0.35f, 0.27f), onSecondaryContainer = tone(sec, s * 0.4f, 0.92f),
            tertiary = tone(ter, 0.65f, 0.72f), onTertiary = tone(ter, 0.6f, 0.15f),
            tertiaryContainer = tone(ter, 0.45f, 0.26f), onTertiaryContainer = tone(ter, 0.6f, 0.90f),
            background = bg, onBackground = tone(h, n, 0.92f),
            surface = bg, onSurface = tone(h, n, 0.92f),
            surfaceVariant = tone(h, n * 1.4f, 0.24f), onSurfaceVariant = tone(h, n * 1.5f, 0.78f),
            surfaceTint = tone(h, s, 0.78f),
            inverseSurface = tone(h, n, 0.90f), inverseOnSurface = tone(h, n, 0.15f),
            outline = tone(h, n, 0.55f), outlineVariant = tone(h, n * 1.2f, 0.28f),
            surfaceDim = bg,
            surfaceBright = tone(h, n, if (amoled) 0.16f else 0.22f),
            surfaceContainerLowest = if (amoled) Color.Black else tone(h, n, 0.04f),
            surfaceContainerLow = tone(h, n, if (amoled) 0.05f else 0.09f),
            surfaceContainer = tone(h, n, if (amoled) 0.075f else 0.115f),
            surfaceContainerHigh = tone(h, n, if (amoled) 0.11f else 0.15f),
            surfaceContainerHighest = tone(h, n, if (amoled) 0.15f else 0.19f),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        val bg = tone(h, n * 2f, 0.985f)
        lightColorScheme(
            primary = tone(h, s, 0.42f), onPrimary = Color.White,
            primaryContainer = tone(h, s, 0.90f), onPrimaryContainer = tone(h, s, 0.14f),
            inversePrimary = tone(h, s, 0.80f),
            secondary = tone(sec, s * 0.35f, 0.40f), onSecondary = Color.White,
            secondaryContainer = tone(sec, s * 0.45f, 0.90f), onSecondaryContainer = tone(sec, s * 0.4f, 0.14f),
            tertiary = tone(ter, 0.55f, 0.34f), onTertiary = Color.White,
            tertiaryContainer = tone(ter, 0.6f, 0.88f), onTertiaryContainer = tone(ter, 0.6f, 0.12f),
            background = bg, onBackground = tone(h, n, 0.10f),
            surface = bg, onSurface = tone(h, n, 0.10f),
            surfaceVariant = tone(h, n * 2f, 0.90f), onSurfaceVariant = tone(h, n * 1.5f, 0.30f),
            surfaceTint = tone(h, s, 0.42f),
            inverseSurface = tone(h, n, 0.18f), inverseOnSurface = tone(h, n, 0.95f),
            outline = tone(h, n, 0.48f), outlineVariant = tone(h, n * 1.5f, 0.82f),
            surfaceDim = tone(h, n * 2f, 0.88f), surfaceBright = bg,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(h, n * 2.5f, 0.965f),
            surfaceContainer = tone(h, n * 2.5f, 0.945f),
            surfaceContainerHigh = tone(h, n * 2.5f, 0.925f),
            surfaceContainerHighest = tone(h, n * 2.5f, 0.905f),
            error = Color(0xFFBA1A1A), onError = Color.White,
            errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        )
    }
}

private val OrbitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private fun orbitTypography(): Typography {
    val base = Typography()
    fun TextStyle.tight(weight: FontWeight, spacing: Float = -0.25f) = copy(fontWeight = weight, letterSpacing = spacing.sp)
    return base.copy(
        displayLarge = base.displayLarge.tight(FontWeight.Bold, -1f),
        displayMedium = base.displayMedium.tight(FontWeight.Bold, -0.8f),
        displaySmall = base.displaySmall.tight(FontWeight.Bold, -0.5f),
        headlineLarge = base.headlineLarge.tight(FontWeight.Bold, -0.5f),
        headlineMedium = base.headlineMedium.tight(FontWeight.Bold, -0.4f),
        headlineSmall = base.headlineSmall.tight(FontWeight.SemiBold),
        titleLarge = base.titleLarge.tight(FontWeight.SemiBold),
        titleMedium = base.titleMedium.tight(FontWeight.SemiBold, 0f),
        titleSmall = base.titleSmall.tight(FontWeight.SemiBold, 0f),
        labelLarge = base.labelLarge.tight(FontWeight.SemiBold, 0.1f),
    )
}

@Composable
fun OrbitTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var scheme = if (useDynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else schemeFromSeed(Color(settings.accent.seed), dark, settings.amoled)
    if (dark && settings.amoled && useDynamic) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF080808), surfaceContainer = Color(0xFF0F0F10),
            surfaceContainerHigh = Color(0xFF17171A), surfaceContainerHighest = Color(0xFF1F1F23),
        )
    }
    // Keep status/navigation bar icons readable.
    val activity = context as? android.app.Activity
    val bgArgb = scheme.background.toArgb()
    SideEffect {
        activity?.window?.let { w ->
            val controller = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            w.decorView.setBackgroundColor(bgArgb)
        }
    }
    CompositionLocalProvider(
        LocalReduceMotion provides settings.reduceMotion,
        LocalCompact provides settings.compact,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(colorScheme = scheme, typography = orbitTypography(), shapes = OrbitShapes, content = content)
    }
}
