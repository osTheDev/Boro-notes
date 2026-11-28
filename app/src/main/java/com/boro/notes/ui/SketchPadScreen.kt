package com.boro.notes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.boro.notes.studio.SketchPad
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanoramaFishEye
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.West
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.boro.notes.sketch.Pt
import com.boro.notes.studio.FABRICS
import com.boro.notes.studio.Pad
import com.boro.notes.studio.PadShape
import com.boro.notes.studio.PadSide
import com.boro.notes.studio.PadStroke
import kotlin.math.roundToInt

const val TAG_SKETCH_PAD = "sketchPad"
const val TAG_PAD_CANVAS = "padCanvas"

private enum class PadTool { DRAW, SHAPE, FILL, ERASE }

private val PEN_SIZES = listOf("Fine" to 2f, "Pen" to 4f, "Bold" to 7f, "Marker" to 14f)
private val ERASER_SIZES = listOf("Small" to 0.01f, "Medium" to 0.022f, "Large" to 0.045f)
/** Auto-level choices, as the angle step in degrees (0 is off). */
private val LEVELS = listOf("Off" to 0, "90°" to 90, "45°" to 45, "15°" to 15)

private fun PadShape.icon(): ImageVector = when (this) {
    PadShape.LINE -> Icons.Filled.HorizontalRule
    PadShape.BOX -> Icons.Filled.CheckBoxOutlineBlank
    PadShape.OVAL -> Icons.Filled.PanoramaFishEye
    PadShape.TRIANGLE -> Icons.Filled.ChangeHistory
}

private fun PadShape.label() = name.lowercase().replaceFirstChar(Char::uppercase)

/**
 * A loose sketch: one finger draws, two fingers zoom and pan. Every tool opens one popup with all
 * its settings. The trace photo can be moved, resized and turned, and the pad grows on any side.
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
    var eraser by rememberSaveable { mutableStateOf(ERASER_SIZES[1].second) }
    var smudge by rememberSaveable { mutableStateOf(false) }
    var fill by rememberSaveable { mutableStateOf(0) }
    var mirror by rememberSaveable { mutableStateOf(false) }
    var straighten by rememberSaveable { mutableStateOf(false) }
    var levelDeg by rememberSaveable { mutableStateOf(0) }
    var movingPhoto by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val live = remember { mutableStateListOf<Pt>() }
    val currentPad by rememberUpdatedState(pad)
    val photoBitmap = rememberStoredImage(pad.photo)
    val photoAspect = photoBitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1f
    val photos = rememberPhotoSource { file ->
        vm.changePad { it.copy(photo = file, showPhoto = true, photoX = 0f, photoY = 0f, photoW = it.padW, photoRot = 0f) }
    }
    if (pad.photo == null && movingPhoto) movingPhoto = false

    // Marker strokes are see-through so they read as a marker over pen lines.
    fun inkFor(width: Float): Long = if (width >= 14f) (ink and 0x00FFFFFFL) or 0x99000000L else ink

    fun commit(stroke: PadStroke?) {
        stroke ?: return
        vm.changePad { p -> Pad.editActive(p) { it + stroke + if (mirror) listOf(Pad.mirror(stroke, p.padW)) else emptyList() } }
    }

    Surface(Modifier.fillMaxSize().testTag(TAG_SKETCH_PAD), color = cs.surface) {
        Column(Modifier.fillMaxSize()) {
            IdeaHeader(draft, vm, collections, what = "sketch")
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().clipToBounds().background(cs.surfaceContainerHighest.copy(alpha = 0.5f)),
            ) {
                val dens = LocalDensity.current
                val padW = minOf(maxWidth - 16.dp, (maxHeight - 16.dp) * (pad.padW / pad.padH))
                val padH = padW * (pad.padH / pad.padW)
                val originX = with(dens) { ((maxWidth - padW) / 2).toPx() }
                val originY = with(dens) { ((maxHeight - padH) / 2).toPx() }
                val unitPx = with(dens) { padW.toPx() } / pad.padW
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(TAG_PAD_CANVAS)
                        .pointerInput(tool, shape, ink, penWidth, eraser, smudge, fill, mirror, straighten, levelDeg, movingPhoto, unitPx, originX, originY, photoAspect) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                fun local(p: Offset) = Pt((p.x - originX - pan.x) / zoom / unitPx, (p.y - originY - pan.y) / zoom / unitPx)
                                down.consume()
                                if (movingPhoto) {
                                    // One finger moves the photo; two fingers resize and turn it.
                                    var started = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                        val move = event.calculatePan()
                                        val scale = event.calculateZoom()
                                        val turn = event.calculateRotation()
                                        if (move == Offset.Zero && scale == 1f && turn == 0f) continue
                                        if (!started) { started = true; vm.checkpoint() }
                                        vm.changePad(record = false) { p ->
                                            val w = (p.photoW * scale).coerceIn(0.1f, 8f)
                                            val grow = w - p.photoW
                                            p.copy(
                                                photoW = w,
                                                photoX = p.photoX - grow / 2 + move.x / zoom / unitPx,
                                                photoY = p.photoY - grow / photoAspect / 2 + move.y / zoom / unitPx,
                                                photoRot = p.photoRot + turn,
                                            )
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                    return@awaitEachGesture
                                }
                                var active = true
                                live.clear()
                                live.add(local(down.position))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        active = false
                                        live.clear()
                                        val centroid = event.calculateCentroid()
                                        val newZoom = (zoom * event.calculateZoom()).coerceIn(0.5f, 8f)
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
                                val p = currentPad
                                when (tool) {
                                    PadTool.DRAW -> commit(Pad.stroke(pts, inkFor(penWidth), penWidth, straighten, if (straighten) levelDeg else 0))
                                    PadTool.SHAPE -> commit(Pad.shape(shape, pts.first(), pts.last(), inkFor(penWidth), penWidth, levelDeg))
                                    PadTool.FILL -> {
                                        val at = pts.last()
                                        vm.changePad { q ->
                                            val once = Pad.fill(q, at, fill)
                                            if (mirror) Pad.fill(once, Pt(q.padW - at.x, at.y), fill) else once
                                        }
                                    }
                                    PadTool.ERASE -> {
                                        val path = if (mirror) pts + pts.map { Pt(p.padW - it.x, it.y) } else pts
                                        val radius = eraser / zoom
                                        vm.changePad { q ->
                                            Pad.editActive(q) { strokes ->
                                                if (smudge) Pad.eraseArea(strokes, path, radius)
                                                else Pad.touched(strokes, path, radius).let { hit -> strokes.filterIndexed { i, _ -> i !in hit } }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    PadSurface(
                        pad, padW,
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
                            .clip(RoundedCornerShape(6.dp))
                            .border(if (movingPhoto) 2.dp else 1.dp, if (movingPhoto) cs.primary else cs.outlineVariant, RoundedCornerShape(6.dp)),
                    ) { unit ->
                        if (mirror) {
                            drawLine(
                                Color(0xFF2E4A7D).copy(alpha = 0.35f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height),
                                strokeWidth = 1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                            )
                        }
                        // The stroke being drawn (and its mirror), or a shape preview.
                        val preview: List<PadStroke> = when {
                            live.isEmpty() -> emptyList()
                            tool == PadTool.DRAW -> listOf(PadStroke(inkFor(penWidth), penWidth, live.flatMap { listOf(it.x, it.y) }))
                            tool == PadTool.SHAPE -> listOfNotNull(Pad.shape(shape, live.first(), live.last(), inkFor(penWidth), penWidth, levelDeg))
                            tool == PadTool.ERASE -> listOf(PadStroke(0x55D32F2F, eraser * 720f, live.flatMap { listOf(it.x, it.y) }))
                            else -> emptyList()
                        }
                        val withMirror = if (mirror && tool != PadTool.ERASE) preview + preview.map { Pad.mirror(it, pad.padW) } else preview
                        drawPadStrokes(withMirror, unit)
                    }
                    Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (movingPhoto) {
                            Chip("Moving photo · Done", cs.primary, cs.onPrimary, "pad-photo-done") { movingPhoto = false }
                        }
                        if (zoom != 1f || pan != Offset.Zero) {
                            Chip("${(zoom * 100).roundToInt()}%", cs.inverseSurface, cs.inverseOnSurface, "pad-zoom-reset", Icons.Filled.ZoomOutMap) { zoom = 1f; pan = Offset.Zero }
                        }
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Drawing tools: each opens one popup with everything it needs.
                ToolGroup(cs.primaryContainer, cs.onPrimaryContainer) {
                    ToolPopup(Icons.Filled.Brush, "Brush", "pad-draw", selected = tool == PadTool.DRAW, accent = Color(ink), onSelect = { tool = PadTool.DRAW; movingPhoto = false }) { _ ->
                        PopupSection("Colour") { ColourDots(INK_COLORS, ink, "pad-ink") { ink = it } }
                        PopupSection("Size") { SizeDots(PEN_SIZES, penWidth, Color(ink), "pad-size") { penWidth = it } }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        ToggleRow("Mirror", "Draw both halves at once", mirror, "pad-mirror") { mirror = it }
                        ToggleRow("Straighten", "Clean straight lines and corners", straighten, "pad-straighten") { straighten = it }
                        PopupSection("Auto-level angles") {
                            ChoiceChips(LEVELS.map { it.first }, LEVELS.indexOfFirst { it.second == levelDeg }, "pad-level") {
                                levelDeg = LEVELS[it].second
                                if (levelDeg > 0) straighten = true
                            }
                        }
                    }
                    ToolPopup(shape.icon(), "Shapes", "pad-shapes", selected = tool == PadTool.SHAPE, accent = Color(ink), onSelect = { tool = PadTool.SHAPE; movingPhoto = false }) { _ ->
                        PopupSection("Shape · drag corner to corner") {
                            ChoiceChips(PadShape.entries.map { it.label() }, shape.ordinal, "pad-shape") { shape = PadShape.entries[it] }
                        }
                        PopupSection("Colour") { ColourDots(INK_COLORS, ink, "pad-shape-ink") { ink = it } }
                        PopupSection("Size") { SizeDots(PEN_SIZES, penWidth, Color(ink), "pad-shape-size") { penWidth = it } }
                        PopupSection("Level lines") {
                            ChoiceChips(LEVELS.map { it.first }, LEVELS.indexOfFirst { it.second == levelDeg }, "pad-shape-level") { levelDeg = LEVELS[it].second }
                        }
                        ToggleRow("Mirror", "Draw both halves at once", mirror, "pad-shape-mirror") { mirror = it }
                    }
                    ToolPopup(
                        Icons.Filled.FormatColorFill, "Fabric fill", "pad-fill", selected = tool == PadTool.FILL,
                        accent = Color(FABRICS[fill].base), onSelect = { tool = PadTool.FILL; movingPhoto = false },
                    ) { close ->
                        PopupSection("Fabric · then tap inside a shape") { FabricDots(fill) { fill = it; close() } }
                        Text(
                            "Fills what your lines enclose, even across several strokes or a small gap. Tap again to change the fabric.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    ToolPopup(Icons.Filled.AutoFixNormal, "Eraser", "pad-erase", selected = tool == PadTool.ERASE, onSelect = { tool = PadTool.ERASE; movingPhoto = false }) { _ ->
                        PopupSection("Rubs out") {
                            ChoiceChips(listOf("Whole line", "Smudge"), if (smudge) 1 else 0, "pad-erase-mode") { smudge = it == 1 }
                        }
                        Text(
                            if (smudge) "Only what the circle passes over, like a real eraser" else "Any line the circle touches",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        PopupSection("Size") { SizeDots(ERASER_SIZES.map { it.first to it.second * 500f }, eraser * 500f, cs.error, "pad-eraser") { eraser = it / 500f } }
                    }
                }
                // The page: layers, the photo to trace, and the canvas size.
                ToolGroup(cs.tertiaryContainer, cs.onTertiaryContainer) {
                    ToolPopup(Icons.Filled.Layers, "Layers", "pad-layers", accent = if (pad.layers.size > 1) cs.primary else null) { _ ->
                        LayersPanel(pad, vm)
                    }
                    ToolPopup(Icons.Filled.Image, "Trace photo", "pad-photo", accent = if (pad.photo != null) cs.primary else null) { close ->
                        ActionRow(Icons.Filled.PhotoCamera, if (pad.photo == null) "Take a photo" else "Retake photo", "pad-photo-camera") { close(); photos.camera() }
                        ActionRow(Icons.Filled.PhotoLibrary, "From gallery", "pad-photo-gallery") { close(); photos.gallery() }
                        if (pad.photo != null) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            ActionRow(Icons.Filled.OpenWith, "Move, resize, turn", "pad-photo-move") {
                                movingPhoto = true
                                if (!pad.showPhoto) vm.changePad { it.copy(showPhoto = true) }
                                close()
                            }
                            PopupSection("Fade") {
                                val fades = listOf("Faint" to 0.25f, "Half" to 0.45f, "Strong" to 0.75f, "Full" to 1f)
                                ChoiceChips(fades.map { it.first }, fades.indexOfFirst { it.second == pad.photoAlpha }, "pad-fade") { i ->
                                    vm.changePad { it.copy(photoAlpha = fades[i].second, showPhoto = true) }
                                }
                            }
                            ToggleRow("Show photo", null, pad.showPhoto, "pad-photo-show") { on -> vm.changePad { it.copy(showPhoto = on) } }
                            ActionRow(Icons.Filled.HideImage, "Remove photo", "pad-photo-remove") { vm.changePad { it.copy(photo = null) }; close() }
                        }
                    }
                    ToolPopup(Icons.Filled.AspectRatio, "Canvas", "pad-canvas") { _ ->
                        PopupSection("Add space") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    Triple(PadSide.LEFT, Icons.Filled.West, "Left"), Triple(PadSide.TOP, Icons.Filled.North, "Top"),
                                    Triple(PadSide.RIGHT, Icons.Filled.East, "Right"), Triple(PadSide.BOTTOM, Icons.Filled.South, "Bottom"),
                                ).forEach { (side, icon, name) ->
                                    LabeledTool(icon, name, "pad-grow-${side.name}") { vm.changePad { Pad.grow(it, side) } }
                                }
                            }
                        }
                        ActionRow(Icons.Filled.CropFree, "Trim to drawing", "pad-trim", enabled = pad.strokes.isNotEmpty()) { vm.changePad { Pad.trim(it) } }
                        Text(
                            "${"%.1f".format(pad.padW)} × ${"%.1f".format(pad.padH)} pages wide/tall",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                ToolGroup(cs.secondaryContainer, cs.onSecondaryContainer) {
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", "pad-undo", enabled = draft.canUndo) { vm.undo() }
                    ToolButton(Icons.AutoMirrored.Filled.Redo, "Redo", "pad-redo", enabled = draft.canRedo) { vm.redo() }
                }
            }
        }
    }
}

private val LAYER_FADES = listOf("Faint" to 0.25f, "Half" to 0.45f, "Strong" to 0.75f, "Full" to 1f)

/** The layers, top first: tap one to draw on it, the eye shows or hides it; the photo sits at the bottom. */
@Composable
private fun LayersPanel(pad: SketchPad, vm: IdeasViewModel) {
    val cs = MaterialTheme.colorScheme
    val active = pad.activeIndex
    Column(Modifier.padding(horizontal = 8.dp)) {
        for (i in pad.layers.indices.reversed()) {
            val layer = pad.layers[i]
            val on = i == active
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (on) cs.primaryContainer else Color.Transparent)
                    .clickable { vm.changePad { it.copy(active = i) } }
                    .padding(start = 10.dp)
                    .testTag("pad-layer-$i"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(layer.name, style = MaterialTheme.typography.bodyMedium, color = if (on) cs.onPrimaryContainer else cs.onSurface)
                    Text(
                        listOfNotNull(
                            if (on) "Drawing here" else null,
                            "${layer.strokes.size} mark${if (layer.strokes.size == 1) "" else "s"}",
                            if (layer.alpha < 1f) "${(layer.alpha * 100).roundToInt()}%" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.changePad { p -> Pad.updateLayer(p, i) { it.copy(visible = !it.visible) } } }, modifier = Modifier.testTag("pad-layer-eye-$i")) {
                    Icon(if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (layer.visible) "Hide ${layer.name}" else "Show ${layer.name}")
                }
            }
        }
        if (pad.photo != null) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Image, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Trace photo", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.changePad { it.copy(showPhoto = !it.showPhoto) } }, modifier = Modifier.testTag("pad-layer-photo-eye")) {
                    Icon(if (pad.showPhoto) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, if (pad.showPhoto) "Hide photo" else "Show photo")
                }
            }
        }
    }
    PopupSection("Fade · ${pad.activeLayer.name}") {
        ChoiceChips(LAYER_FADES.map { it.first }, LAYER_FADES.indexOfFirst { it.second == pad.activeLayer.alpha }, "pad-layer-fade") { f ->
            vm.changePad { p -> Pad.updateLayer(p, active) { it.copy(alpha = LAYER_FADES[f].second, visible = true) } }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    ActionRow(Icons.Filled.Add, "New layer", "pad-layer-add") { vm.changePad { Pad.addLayer(it) } }
    ActionRow(Icons.Filled.ArrowUpward, "Move up", "pad-layer-up", enabled = active < pad.layers.size - 1) { vm.changePad { Pad.moveLayer(it, active, up = true) } }
    ActionRow(Icons.Filled.ArrowDownward, "Move down", "pad-layer-down", enabled = active > 0) { vm.changePad { Pad.moveLayer(it, active, up = false) } }
    ActionRow(Icons.Filled.Delete, "Delete ${pad.activeLayer.name}", "pad-layer-delete", enabled = pad.layers.size > 1) { vm.changePad { Pad.deleteLayer(it, active) } }
}

/** A small pill button over the canvas. */
@Composable
private fun Chip(text: String, bg: Color, fg: Color, tag: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

/** Fabric swatches, drawn as cloth (twill grain, stripes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FabricDots(selected: Int, onPick: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FABRICS.forEachIndexed { i, f ->
                val on = i == selected
                Canvas(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(if (on) 3.dp else 1.dp, if (on) cs.primary else cs.outline, CircleShape)
                        .clickable { onPick(i) }
                        .testTag("fabric-$i"),
                ) {
                    val path = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
                    fillFabric(path, f, density)
                }
            }
        }
        Text(FABRICS.getOrNull(selected)?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
