package com.boro.notes.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A colour choice in a palette. Transparent means "none" and sits in the middle of the wheel. */
internal data class Swatch(val name: String, val color: Color, val tag: String, val selected: Boolean)

/** Ease out with a little overshoot, so swatches land with a bounce. */
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/**
 * A paint-palette wheel: the swatches spiral out from the middle into a ring when it opens,
 * each a beat after the last. A "none" swatch (transparent) sits in the centre.
 */
@Composable
internal fun PaletteWheel(swatches: List<Swatch>, onPick: (Swatch) -> Unit, radius: Dp = 78.dp, swatchSize: Dp = 38.dp) {
    val ring = swatches.filter { it.color != Color.Transparent }
    val none = swatches.firstOrNull { it.color == Color.Transparent }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing)) }
    val stagger = 0.45f
    Box(
        Modifier.padding(8.dp).size(radius * 2 + swatchSize),
        contentAlignment = Alignment.Center,
    ) {
        // Centre: "none" if offered, otherwise a little palette.
        Box(
            Modifier.graphicsLayer {
                val t = progress.value
                scaleX = 0.6f + 0.4f * t; scaleY = scaleX; alpha = t
            },
        ) {
            if (none != null) {
                SwatchDot(none, swatchSize + 6.dp) { onPick(none) }
            } else {
                Icon(Icons.Filled.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
        }
        ring.forEachIndexed { i, s ->
            val n = ring.size
            val home = -90f + 360f * i / n
            fun local(p: Float): Float = ((p - stagger * i / n) / (1f - stagger)).coerceIn(0f, 1f)
            Box(
                Modifier
                    .offset {
                        val t = local(progress.value)
                        // Spiral: start a third of a turn back and swing into place while moving out.
                        val angle = Math.toRadians((home - (1f - t) * 120f).toDouble())
                        val r = radius.toPx() * easeOutBack(t)
                        IntOffset((cos(angle) * r).roundToInt(), (sin(angle) * r).roundToInt())
                    }
                    .graphicsLayer {
                        val t = local(progress.value)
                        scaleX = 0.3f + 0.7f * easeOutBack(t); scaleY = scaleX
                        alpha = (t * 2f).coerceAtMost(1f)
                        rotationZ = (1f - t) * -90f
                    },
            ) { SwatchDot(s, swatchSize) { onPick(s) } }
        }
    }
}

@Composable
private fun SwatchDot(s: Swatch, size: Dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .bounce(source)
            .clip(CircleShape)
            .background(if (s.color == Color.Transparent) cs.surface else s.color)
            .border(if (s.selected) 3.dp else 1.dp, if (s.selected) cs.primary else cs.outline, CircleShape)
            .clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
            .testTag(s.tag)
            .semantics { contentDescription = s.name },
        contentAlignment = Alignment.Center,
    ) {
        if (s.color == Color.Transparent) {
            Canvas(Modifier.size(size * 0.55f)) {
                drawLine(Color(0xFFE53935), Offset(0f, this.size.height), Offset(this.size.width, 0f), 2.dp.toPx())
            }
        } else if (s.selected) {
            Icon(
                Icons.Filled.Check, null, Modifier.size(18.dp),
                tint = if (s.color.luminance() > 0.5f || s.color.alpha < 0.6f) Color.Black else Color.White,
            )
        }
    }
}

/** Shrinks a little while [source] is pressed and springs back, so taps feel physical. */
internal fun Modifier.bounce(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fades and rises into place [index] beats after the screen appears. */
internal fun Modifier.popIn(index: Int): Modifier = composed {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index)
        t.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = t.value.coerceIn(0f, 1f)
        translationY = (1f - t.value) * 40.dp.toPx()
        scaleX = 0.92f + 0.08f * t.value; scaleY = scaleX
    }
}
