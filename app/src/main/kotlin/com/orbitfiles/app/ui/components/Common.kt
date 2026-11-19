package com.orbitfiles.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbitfiles.app.ops.OpStatus
import com.orbitfiles.app.ops.OpTask
import com.orbitfiles.app.ui.theme.LocalReduceMotion
import com.orbitfiles.app.ui.theme.Motion
import com.orbitfiles.core.Formatters

/** Press-to-shrink spring feedback with optional long press and haptics (M3 Expressive feel). */
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.bouncy(reduce), label = "press")
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(),
            enabled = enabled,
            onLongClick = onLongClick?.let { lc -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); lc() } },
            onClick = onClick,
        )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Rounded, thick progress bar used for storage usage. */
@Composable
fun UsageBar(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 10.dp) {
    val reduce = LocalReduceMotion.current
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.smooth(reduce), label = "usage")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = size.height / 2
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        if (animated > 0f) {
            drawRoundRect(color, size = Size((size.width * animated).coerceAtLeast(size.height), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        }
    }
}

data class RingSegment(val value: Float, val color: Color)

/** Segmented donut with rounded caps and gaps (storage breakdown). */
@Composable
fun SegmentRing(segments: List<RingSegment>, modifier: Modifier = Modifier, stroke: Dp = 18.dp, center: @Composable () -> Unit = {}) {
    val reduce = LocalReduceMotion.current
    // Sweep-in animation on first display.
    val sweep = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, Motion.smooth(reduce)) }
    val progress = sweep.value
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(sw))
            val total = segments.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0 } ?: return@Canvas
            val gap = if (segments.count { it.value / total > 0.01f } > 1) 5f else 0f
            var start = -90f
            segments.forEach { s ->
                val sweep = 360f * (s.value / total) * progress
                if (sweep > gap + 0.5f) {
                    drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
                }
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun InputDialog(
    title: String,
    initial: String = "",
    label: String = "Nome",
    confirm: String = "OK",
    selectBaseName: Boolean = true,
    validate: (String) -> String? = { null },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val dot = initial.lastIndexOf('.')
    val selEnd = if (selectBaseName && dot > 0) dot else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, selEnd))) }
    val error = if (value.text.isEmpty()) null else validate(value.text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                isError = error != null,
                supportingText = { if (error != null) Text(error) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (error == null && value.text.isNotBlank()) onConfirm(value.text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                shape = MaterialTheme.shapes.medium,
            )
        },
        confirmButton = {
            TextButton(enabled = error == null && value.text.isNotBlank(), onClick = { onConfirm(value.text.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null) } },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/** Floating card showing a running transfer with speed, ETA and cancel. */
@Composable
fun OperationCard(task: OpTask, onCancel: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val p = task.progress
    val reduce = LocalReduceMotion.current
    val fraction by animateFloatAsState(p?.fraction ?: 0f, Motion.smooth(reduce), label = "op")
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = when (task.status) {
                        OpStatus.RUNNING -> buildString {
                            if (p != null && p.totalBytes > 0) append("${Formatters.size(p.doneBytes)} de ${Formatters.size(p.totalBytes)}")
                            else if (p != null && p.totalItems > 0) append("${p.doneItems} de ${p.totalItems}")
                            else append("Preparando…")
                            if (task.speed > 0) append(" · ${Formatters.speed(task.speed)}")
                            task.etaMillis?.let { if (it > 1500) append(" · ${Formatters.duration(it)} restantes") }
                        }
                        else -> task.message ?: ""
                    }
                    Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = if (task.status == OpStatus.RUNNING) onCancel else onDismiss) { Icon(Icons.Rounded.Close, "Cancelar") }
            }
            if (task.status == OpStatus.RUNNING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp).height(6.dp),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Small colored dot + label legend item. */
@Composable
fun LegendItem(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun AnimatedPresence(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) { content() }
}
