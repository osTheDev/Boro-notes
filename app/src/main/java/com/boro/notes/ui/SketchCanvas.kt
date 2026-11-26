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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.boro.notes.sketch.Edits
import com.boro.notes.sketch.Geometry
import com.boro.notes.sketch.LineStyle
import com.boro.notes.sketch.Stamp
import com.boro.notes.sketch.Pt
import com.boro.notes.sketch.Shape
import kotlin.math.ceil
import kotlin.math.floor

/** Size of one grid square at 100% zoom. */
private val GRID_UNIT = 20.dp
private val TOUCH_TOLERANCE = 16.dp
private val HANDLE_TOUCH = 24.dp

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
    /** Points tool: the shape whose points are shown as draggable handles. */
    editing: Shape? = null,
    selectedPoint: Int? = null,
    /** Shapes picked with the Select tool, highlighted. */
    selection: Set<Int> = emptySet(),
    /** Called when a finger goes down (grid units, tolerance); true means the tool drags instead of drawing. */
    onDragStart: ((Pt, Float) -> Boolean)? = null,
    onDrag: (Pt) -> Unit = {},
    onDragEnd: () -> Unit = {},
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
    val dragStart by rememberUpdatedState(onDragStart)
    val dragMove by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val handleTolerancePx = with(LocalDensity.current) { HANDLE_TOUCH.toPx() }

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
                    val takesDrag = dragStart?.invoke(toWorld(down.position), handleTolerancePx / (unitPx * scale)) == true
                    if (takesDrag) {
                        // Dragging a point, or moving the selection.
                        var dragged = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragged = true
                            if (dragged) dragMove(toWorld(change.position))
                            change.consume()
                        }
                        dragEnd()
                        if (!dragged) tapCallback(toWorld(down.position), tolerancePx / (unitPx * scale))
                        return@awaitEachGesture
                    }
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
        val toScreen = { p: Pt -> Offset(p.x * step + offsetX, p.y * step + offsetY) }
        if (selection.isNotEmpty()) drawSelection(shapes, selection, inkPreview, toScreen)
        drawShapes(shapes, ink, 2.5.dp.toPx(), toScreen)
        editing?.let { drawHandles(it, selectedPoint, step, offsetX, offsetY, inkPreview, background) }
        val builder = previewBuilder
        val previewed = if (builder != null && preview.size > 1) builder(preview.map(::toWorld)) else null
        if (previewed != null) {
            drawShapes(listOf(previewed), inkPreview, 2.5.dp.toPx(), toScreen)
        } else if (preview.size > 1 && builder == null) {
            val path = Path().apply {
                moveTo(preview[0].x, preview[0].y)
                preview.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, inkPreview, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Points of the shape being edited: filled squares-ish dots for corners, rings for smooth points. */
private fun DrawScope.drawHandles(
    shape: Shape,
    selected: Int?,
    step: Float,
    offsetX: Float,
    offsetY: Float,
    accent: Color,
    background: Color,
) {
    val r = 7.dp.toPx()
    shape.points.forEachIndexed { i, p ->
        val c = Offset(p.x * step + offsetX, p.y * step + offsetY)
        val radius = if (i == selected) r * 1.5f else r
        val corner = shape.corners.getOrElse(i) { true }
        drawCircle(if (corner) accent else background, radius, c)
        drawCircle(accent, radius, c, style = Stroke(2.dp.toPx()))
        if (i == selected) drawCircle(accent.copy(alpha = 0.25f), radius * 2f, c)
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

/** Topstitch thread colour, like contrast denim stitching. */
val THREAD = Color(0xFFE0A458)
private val COPPER = Color(0xFFB87333)
private val BRASS = Color(0xFFC9A227)
private val BUTTON_METAL = Color(0xFF8A8D91)

private fun shapePath(shape: Shape, toScreen: (Pt) -> Offset): Path? {
    val outline = Geometry.outline(shape)
    if (outline.size < 2) return null
    return Path().apply {
        val first = toScreen(outline[0])
        moveTo(first.x, first.y)
        for (k in 1 until outline.size) {
            val o = toScreen(outline[k])
            lineTo(o.x, o.y)
        }
        if (shape.closed) close()
    }
}

/** Draws shapes with fills, patterns, stitching and hardware; [toScreen] maps grid units to pixels. */
fun DrawScope.drawShapes(shapes: List<Shape>, ink: Color, strokeWidth: Float, toScreen: (Pt) -> Offset) {
    val unit = (toScreen(Pt(1f, 0f)) - toScreen(Pt(0f, 0f))).x
    val stitchEffect = PathEffect.dashPathEffect(floatArrayOf(0.28f * unit, 0.18f * unit))
    clipRect {
        for (shape in shapes) {
            shape.stamp?.let { stamp ->
                drawStamp(stamp, toScreen(shape.points.first()), stamp.radius * unit)
                continue
            }
            val path = shapePath(shape, toScreen) ?: continue
            shape.fill?.let { drawPath(path, Color(it).copy(alpha = 0.65f)) }
            shape.pattern?.let { pattern ->
                clipPath(path) {
                    for ((a, b) in Edits.patternLines(shape, pattern)) {
                        drawLine(THREAD, toScreen(a), toScreen(b), strokeWidth * 0.6f, pathEffect = stitchEffect)
                    }
                }
            }
            if (!shape.stroke) continue
            if (shape.style == LineStyle.STITCH) {
                drawPath(path, THREAD, style = Stroke(strokeWidth * 0.8f, cap = StrokeCap.Round, pathEffect = stitchEffect))
            } else {
                drawPath(path, ink, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun DrawScope.drawStamp(stamp: Stamp, c: Offset, r: Float) {
    val color = when (stamp) {
        Stamp.RIVET -> COPPER
        Stamp.SNAP -> BRASS
        Stamp.BUTTON -> BUTTON_METAL
    }
    drawCircle(color, r, c)
    drawCircle(Color.Black.copy(alpha = 0.35f), r, c, style = Stroke(r * 0.18f))
    when (stamp) {
        Stamp.RIVET -> drawCircle(Color.White.copy(alpha = 0.35f), r * 0.35f, c - Offset(r * 0.25f, r * 0.25f))
        Stamp.SNAP -> drawCircle(Color.Black.copy(alpha = 0.3f), r * 0.55f, c, style = Stroke(r * 0.12f))
        Stamp.BUTTON -> {
            drawCircle(Color.Black.copy(alpha = 0.3f), r * 0.7f, c, style = Stroke(r * 0.1f))
            drawCircle(Color.Black.copy(alpha = 0.45f), r * 0.18f, c)
        }
    }
}

/** A soft glow under selected shapes plus a dashed box around them. */
private fun DrawScope.drawSelection(shapes: List<Shape>, selection: Set<Int>, accent: Color, toScreen: (Pt) -> Offset) {
    val chosen = selection.mapNotNull { shapes.getOrNull(it) }
    for (shape in chosen) {
        if (shape.stamp != null) {
            val unit = (toScreen(Pt(1f, 0f)) - toScreen(Pt(0f, 0f))).x
            drawCircle(accent.copy(alpha = 0.3f), (shape.stamp.radius + 0.25f) * unit, toScreen(shape.points.first()))
            continue
        }
        val path = shapePath(shape, toScreen) ?: continue
        drawPath(path, accent.copy(alpha = 0.3f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val b = Edits.bounds(chosen) ?: return
    val tl = toScreen(Pt(b.minX - 0.3f, b.minY - 0.3f))
    val br = toScreen(Pt(b.maxX + 0.3f, b.maxY + 0.3f))
    drawRect(
        accent,
        topLeft = tl,
        size = androidx.compose.ui.geometry.Size(br.x - tl.x, br.y - tl.y),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
    )
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
