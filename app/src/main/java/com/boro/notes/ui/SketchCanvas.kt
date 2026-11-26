package com.boro.notes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.boro.notes.sketch.Geometry
import com.boro.notes.sketch.Pt
import com.boro.notes.sketch.Shape
import kotlin.math.ceil
import kotlin.math.floor

/** Size of one grid square at 100% zoom. */
private val GRID_UNIT = 20.dp
private val TOUCH_TOLERANCE = 16.dp

const val TAG_CANVAS = "sketchCanvas"

/**
 * The drawing surface. One finger draws (or taps to fill/erase); two fingers pan and zoom.
 * Strokes are handed back in grid units.
 */
@Composable
fun SketchCanvas(
    shapes: List<Shape>,
    onStroke: (List<Pt>) -> Unit,
    onTap: (Pt, Float) -> Unit,
    modifier: Modifier = Modifier,
    /** When set, the stroke in progress is previewed as the finished shape instead of the raw finger path. */
    previewShape: ((List<Pt>) -> Shape?)? = null,
) {
    val unitPx = with(LocalDensity.current) { GRID_UNIT.toPx() }
    val tolerancePx = with(LocalDensity.current) { TOUCH_TOLERANCE.toPx() }
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(unitPx) }
    var offsetY by rememberSaveable { mutableFloatStateOf(unitPx) }
    var preview by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val strokeCallback by rememberUpdatedState(onStroke)
    val tapCallback by rememberUpdatedState(onTap)
    val previewBuilder by rememberUpdatedState(previewShape)

    fun toWorld(o: Offset) = Pt((o.x - offsetX) / (unitPx * scale), (o.y - offsetY) / (unitPx * scale))

    val gridMinor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val gridMajor = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val inkPreview = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surface

    Canvas(
        modifier
            .testTag(TAG_CANVAS)
            .pointerInput(unitPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val stroke = mutableListOf(down.position)
                    var multiTouch = false
                    var moved = false
                    preview = stroke.toList()
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size > 1) {
                            multiTouch = true
                            preview = emptyList()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            val newScale = (scale * zoom).coerceIn(0.4f, 5f)
                            val applied = newScale / scale
                            offsetX = centroid.x - (centroid.x - offsetX) * applied + pan.x
                            offsetY = centroid.y - (centroid.y - offsetY) * applied + pan.y
                            scale = newScale
                            event.changes.forEach { it.consume() }
                        } else if (!multiTouch) {
                            val change = pressed.first()
                            stroke += change.position
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                moved = true
                            }
                            preview = stroke.toList()
                            change.consume()
                        }
                    }
                    preview = emptyList()
                    if (!multiTouch) {
                        if (moved) {
                            strokeCallback(stroke.map(::toWorld))
                        } else {
                            tapCallback(toWorld(down.position), tolerancePx / (unitPx * scale))
                        }
                    }
                }
            }
    ) {
        drawRect(background)
        val step = unitPx * scale
        drawGrid(step, offsetX, offsetY, gridMinor, gridMajor)
        drawShapes(shapes, ink, 2.5.dp.toPx()) { p -> Offset(p.x * step + offsetX, p.y * step + offsetY) }
        val builder = previewBuilder
        val previewed = if (builder != null && preview.size > 1) builder(preview.map(::toWorld)) else null
        if (previewed != null) {
            drawShapes(listOf(previewed), inkPreview, 2.5.dp.toPx()) { p -> Offset(p.x * step + offsetX, p.y * step + offsetY) }
        } else if (preview.size > 1 && builder == null) {
            val path = Path().apply {
                moveTo(preview[0].x, preview[0].y)
                preview.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, inkPreview, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun DrawScope.drawGrid(step: Float, offsetX: Float, offsetY: Float, minor: Color, major: Color) {
    if (step < 4f) return
    val firstX = floor(-offsetX / step).toInt()
    val lastX = ceil((size.width - offsetX) / step).toInt()
    val firstY = floor(-offsetY / step).toInt()
    val lastY = ceil((size.height - offsetY) / step).toInt()
    val showMinor = step >= 10f
    for (i in firstX..lastX) {
        val isMajor = i % 4 == 0
        if (!isMajor && !showMinor) continue
        val x = i * step + offsetX
        drawLine(if (isMajor) major else minor, Offset(x, 0f), Offset(x, size.height), strokeWidth = if (isMajor) 1.5f else 1f)
    }
    for (j in firstY..lastY) {
        val isMajor = j % 4 == 0
        if (!isMajor && !showMinor) continue
        val y = j * step + offsetY
        drawLine(if (isMajor) major else minor, Offset(0f, y), Offset(size.width, y), strokeWidth = if (isMajor) 1.5f else 1f)
    }
}

/** Draws shapes with fills; [toScreen] maps grid units to pixels. Used by the canvas and thumbnails. */
fun DrawScope.drawShapes(shapes: List<Shape>, ink: Color, strokeWidth: Float, toScreen: (Pt) -> Offset) {
    clipRect {
        for (shape in shapes) {
            val outline = Geometry.outline(shape)
            if (outline.size < 2) continue
            val path = Path().apply {
                val first = toScreen(outline[0])
                moveTo(first.x, first.y)
                for (k in 1 until outline.size) {
                    val o = toScreen(outline[k])
                    lineTo(o.x, o.y)
                }
                if (shape.closed) close()
            }
            shape.fill?.let { drawPath(path, Color(it).copy(alpha = 0.65f)) }
            drawPath(path, ink, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Fits shapes into the draw area, for list thumbnails. */
fun DrawScope.drawThumbnail(shapes: List<Shape>, ink: Color) {
    val all = shapes.flatMap { Geometry.outline(it) }
    if (all.isEmpty()) return
    val minX = all.minOf { it.x }
    val maxX = all.maxOf { it.x }
    val minY = all.minOf { it.y }
    val maxY = all.maxOf { it.y }
    val w = (maxX - minX).coerceAtLeast(1f)
    val h = (maxY - minY).coerceAtLeast(1f)
    val pad = 4f
    val s = minOf((size.width - 2 * pad) / w, (size.height - 2 * pad) / h)
    val dx = (size.width - w * s) / 2
    val dy = (size.height - h * s) / 2
    drawShapes(shapes, ink, 1.5f) { p -> Offset((p.x - minX) * s + dx, (p.y - minY) * s + dy) }
}
