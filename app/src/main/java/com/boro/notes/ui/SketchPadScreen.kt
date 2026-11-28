package com.boro.notes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.PanoramaFishEye
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.boro.notes.sketch.Pt
import com.boro.notes.studio.FABRICS
import com.boro.notes.studio.PAD_HEIGHT
import com.boro.notes.studio.Pad
import com.boro.notes.studio.PadShape
import com.boro.notes.studio.PadStroke
import kotlin.math.roundToInt

const val TAG_SKETCH_PAD = "sketchPad"
const val TAG_PAD_CANVAS = "padCanvas"

private enum class PadTool { DRAW, SHAPE, FILL, ERASE }

private val PEN_SIZES = listOf("Fine" to 2f, "Pen" to 4f, "Bold" to 7f, "Marker" to 14f)

private fun PadShape.icon(): ImageVector = when (this) {
    PadShape.LINE -> Icons.Filled.HorizontalRule
    PadShape.BOX -> Icons.Filled.CheckBoxOutlineBlank
    PadShape.OVAL -> Icons.Filled.PanoramaFishEye
    PadShape.TRIANGLE -> Icons.Filled.ChangeHistory
}

/**
 * A loose sketch: one finger draws, two fingers zoom and pan. Mirror draws both halves at once;
 * auto-straighten (off by default here) cleans strokes up; a faded photo can sit underneath to trace.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SketchPadScreen(draft: IdeaDraft, vm: IdeasViewModel, collections: List<String>) {
    val pad = draft.pad
    val cs = MaterialTheme.colorScheme
    var tool by rememberSaveable { mutableStateOf(PadTool.DRAW) }
    var shape by rememberSaveable { mutableStateOf(PadShape.BOX) }
    var ink by rememberSaveable { mutableStateOf(INK_COLORS[0]) }
    var penWidth by rememberSaveable { mutableStateOf(4f) }
    var fill by rememberSaveable { mutableStateOf(0) }
    var mirror by rememberSaveable { mutableStateOf(false) }
    var straighten by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val live = remember { mutableStateListOf<Pt>() }
    val currentPad by rememberUpdatedState(pad)
    val photos = rememberPhotoSource { file -> vm.changePad { it.copy(photo = file, showPhoto = true) } }

    // Marker strokes are see-through so they read as a marker over pen lines.
    fun inkFor(width: Float): Long = if (width >= 14f) (ink and 0x00FFFFFFL) or 0x99000000L else ink

    fun commit(stroke: PadStroke?) {
        stroke ?: return
        vm.changePad { p -> p.copy(strokes = p.strokes + stroke + if (mirror) listOf(Pad.mirror(stroke)) else emptyList()) }
    }

    Surface(Modifier.fillMaxSize().testTag(TAG_SKETCH_PAD), color = cs.surface) {
        Column(Modifier.fillMaxSize()) {
            IdeaHeader(draft, vm, collections, what = "sketch")
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().background(cs.surfaceContainerHighest.copy(alpha = 0.5f)),
            ) {
                val dens = LocalDensity.current
                val padW = minOf(maxWidth - 16.dp, (maxHeight - 16.dp) / PAD_HEIGHT)
                val padH = padW * PAD_HEIGHT
                val originX = with(dens) { ((maxWidth - padW) / 2).toPx() }
                val originY = with(dens) { ((maxHeight - padH) / 2).toPx() }
                val wPx = with(dens) { padW.toPx() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(TAG_PAD_CANVAS)
                        .pointerInput(tool, shape, ink, penWidth, fill, mirror, straighten, wPx, originX, originY) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun local(p: Offset) = Pt((p.x - originX - pan.x) / zoom / wPx, (p.y - originY - pan.y) / zoom / wPx)
                                var active = true
                                live.clear()
                                live.add(local(down.position))
                                down.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        active = false
                                        live.clear()
                                        val centroid = event.calculateCentroid()
                                        val newZoom = (zoom * event.calculateZoom()).coerceIn(1f, 6f)
                                        val k = newZoom / zoom
                                        val anchor = centroid - Offset(originX, originY)
                                        pan = anchor - (anchor - pan) * k + event.calculatePan()
                                        zoom = newZoom
                                        event.changes.forEach { it.consume() }
                                    } else if (active) {
                                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                        c.consume()
                                        live.add(local(c.position))
                                    }
                                }
                                val pts = live.toList()
                                live.clear()
                                if (!active || pts.isEmpty()) return@awaitEachGesture
                                when (tool) {
                                    PadTool.DRAW -> commit(Pad.stroke(pts, inkFor(penWidth), penWidth, straighten))
                                    PadTool.SHAPE -> commit(Pad.shape(shape, pts.first(), pts.last(), inkFor(penWidth), penWidth))
                                    PadTool.FILL -> {
                                        val at = pts.last()
                                        val target = Pad.fillTarget(currentPad.strokes, at)
                                        if (target != null) vm.changePad { p ->
                                            val mirrorTarget = if (mirror) Pad.fillTarget(p.strokes, Pt(1f - at.x, at.y)) else null
                                            p.copy(strokes = p.strokes.mapIndexed { i, s -> if (i == target || i == mirrorTarget) s.copy(fill = fill) else s })
                                        }
                                    }
                                    PadTool.ERASE -> {
                                        val path = if (mirror) pts + pts.map { Pt(1f - it.x, it.y) } else pts
                                        val hit = Pad.touched(currentPad.strokes, path, 0.02f / zoom)
                                        if (hit.isNotEmpty()) vm.changePad { p -> p.copy(strokes = p.strokes.filterIndexed { i, _ -> i !in hit }) }
                                    }
                                }
                            }
                        },
                ) {
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .offset { IntOffset(originX.roundToInt(), originY.roundToInt()) }
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            }
                            .size(padW, padH)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, cs.outlineVariant, RoundedCornerShape(6.dp))
                            .background(Color(0xFFFBF8F1)),
                    ) {
                        if (pad.photo != null && pad.showPhoto) {
                            StoredImage(pad.photo, Modifier.matchParentSize(), ContentScale.Crop, alpha = pad.photoAlpha)
                        }
                        Canvas(Modifier.matchParentSize()) {
                            if (mirror) {
                                drawLine(
                                    Color(0xFF2E4A7D).copy(alpha = 0.35f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height),
                                    strokeWidth = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                )
                            }
                            drawPadStrokes(pad.strokes)
                            // The stroke being drawn (and its mirror), or a shape preview.
                            val preview: List<PadStroke> = when {
                                live.isEmpty() -> emptyList()
                                tool == PadTool.DRAW -> listOf(PadStroke(inkFor(penWidth), penWidth, live.flatMap { listOf(it.x, it.y) }))
                                tool == PadTool.SHAPE -> listOfNotNull(Pad.shape(shape, live.first(), live.last(), inkFor(penWidth), penWidth))
                                tool == PadTool.ERASE -> listOf(PadStroke(0x55D32F2F, 18f, live.flatMap { listOf(it.x, it.y) }))
                                else -> emptyList()
                            }
                            val withMirror = if (mirror && tool != PadTool.ERASE) preview + preview.map(Pad::mirror) else preview
                            drawPadStrokes(withMirror)
                        }
                    }
                    if (zoom > 1.01f || pan != Offset.Zero) {
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(50))
                                .background(cs.inverseSurface)
                                .clickable { zoom = 1f; pan = Offset.Zero }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.ZoomOutMap, null, tint = cs.inverseOnSurface, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${(zoom * 100).roundToInt()}%", color = cs.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (tool == PadTool.FILL) {
                FabricRow(fill) { fill = it }
            }
            FlowRow(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Draw tools.
                ToolGroup(cs.primaryContainer, cs.onPrimaryContainer) {
                    ToolButton(Icons.Filled.Brush, "Draw", "pad-draw", selected = tool == PadTool.DRAW) { tool = PadTool.DRAW }
                    ToolMenu(shape.icon(), "Shapes", "pad-shapes", accent = if (tool == PadTool.SHAPE) LocalAccent() else null) { close ->
                        MenuLabel("Drag corner to corner")
                        PadShape.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name.lowercase().replaceFirstChar(Char::uppercase)) },
                                leadingIcon = { Icon(s.icon(), null) },
                                onClick = { shape = s; tool = PadTool.SHAPE; close() },
                                modifier = Modifier.testTag("pad-shape-${s.name}"),
                            )
                        }
                    }
                    ToolButton(Icons.Filled.FormatColorFill, "Fabric fill", "pad-fill", selected = tool == PadTool.FILL) { tool = PadTool.FILL }
                    ToolButton(Icons.Filled.AutoFixNormal, "Eraser", "pad-erase", selected = tool == PadTool.ERASE) { tool = PadTool.ERASE }
                }
                // Ink and pen size.
                ToolGroup(cs.secondaryContainer, cs.onSecondaryContainer) {
                    ToolMenu(Icons.Filled.Palette, "Ink colour", "pad-ink", accent = Color(ink)) { close ->
                        MenuLabel("Ink")
                        SwatchGrid(INK_COLORS.mapIndexed { i, c -> Swatch("Ink ${i + 1}", Color(c), "pad-ink-$i", c == ink) }) { s ->
                            ink = INK_COLORS[s.tag.removePrefix("pad-ink-").toInt()]
                            if (tool == PadTool.ERASE || tool == PadTool.FILL) tool = PadTool.DRAW
                            close()
                        }
                    }
                    ToolMenu(Icons.Filled.LineWeight, "Pen size", "pad-size") { close ->
                        MenuLabel("Pen")
                        PEN_SIZES.forEach { (name, w) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                leadingIcon = { Box(Modifier.size(w.dp + 4.dp).background(Color(ink), CircleShape)) },
                                trailingIcon = { if (penWidth == w) Text("✓") },
                                onClick = { penWidth = w; close() },
                            )
                        }
                    }
                }
                // Helpers: mirror, straighten, the photo to trace.
                ToolGroup(cs.tertiaryContainer, cs.onTertiaryContainer) {
                    ToolButton(Icons.Filled.Flip, if (mirror) "Mirror on" else "Mirror off", "pad-mirror", selected = mirror) { mirror = !mirror }
                    ToolButton(Icons.Filled.Straighten, if (straighten) "Auto-straighten on" else "Auto-straighten off", "pad-straighten", selected = straighten) { straighten = !straighten }
                    ToolMenu(Icons.Filled.Image, "Trace a photo", "pad-photo", accent = if (pad.photo != null) LocalAccent() else null) { close ->
                        MenuLabel("Photo to trace")
                        DropdownMenuItem(text = { Text("Take a photo") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) }, onClick = { close(); photos.camera() })
                        DropdownMenuItem(text = { Text("From gallery") }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) }, onClick = { close(); photos.gallery() })
                        if (pad.photo != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (pad.showPhoto) "Hide photo" else "Show photo") },
                                leadingIcon = { Icon(if (pad.showPhoto) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null) },
                                onClick = { vm.changePad { it.copy(showPhoto = !it.showPhoto) }; close() },
                            )
                            listOf("Faint" to 0.25f, "Half" to 0.45f, "Strong" to 0.75f).forEach { (name, a) ->
                                DropdownMenuItem(
                                    text = { Text("Fade: $name") },
                                    leadingIcon = { Icon(Icons.Filled.Opacity, null) },
                                    trailingIcon = { if (pad.photoAlpha == a) Text("✓") },
                                    onClick = { vm.changePad { it.copy(photoAlpha = a, showPhoto = true) }; close() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Remove photo") },
                                leadingIcon = { Icon(Icons.Filled.HideImage, null) },
                                onClick = { vm.changePad { it.copy(photo = null) }; close() },
                            )
                        }
                    }
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", "pad-undo", enabled = draft.canUndo) { vm.undo() }
                    ToolButton(Icons.AutoMirrored.Filled.Redo, "Redo", "pad-redo", enabled = draft.canRedo) { vm.redo() }
                }
            }
        }
    }
}

@Composable
private fun LocalAccent(): Color = MaterialTheme.colorScheme.primary

/** Fabric swatches: tap one, then tap inside a closed shape. */
@Composable
internal fun FabricRow(selected: Int, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FABRICS.forEachIndexed { i, f ->
            val on = i == selected
            Canvas(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(if (on) 3.dp else 1.dp, if (on) cs.primary else cs.outline, CircleShape)
                    .clickable { onPick(i) }
                    .testTag("fabric-$i"),
            ) {
                val path = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
                fillFabric(path, f, density)
            }
        }
        Text(
            FABRICS.getOrNull(selected)?.name.orEmpty() + " · tap inside a closed shape",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}
