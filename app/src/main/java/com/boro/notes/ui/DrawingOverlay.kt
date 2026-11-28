package com.boro.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.boro.notes.notes.DrawingBlock
import com.boro.notes.notes.InkStroke
import kotlin.math.hypot

const val TAG_DRAWING_OVERLAY = "drawingOverlay"
const val TAG_DRAWING_CANVAS = "drawingCanvas"

/** Canvas colours: null follows the page as a soft card, 0 is clear (straight on the page). */
private val DRAWING_PAPERS: List<Pair<String, Long?>> = listOf(
    "Card" to null, "Clear" to 0L, "White" to 0xFFFFFFFF, "Cream" to 0xFFF6EFDF,
    "Kraft" to 0xFFD9C3A0, "Sage" to 0xFFDCE5D4, "Indigo" to 0xFF1F2A44, "Charcoal" to 0xFF2A2A2A,
)
private val DRAWING_PATTERNS = listOf("Plain", "Dots", "Grid", "Lines")

/**
 * A full-screen paint overlay for one drawing. Strokes only happen here, so a drawing placed in a
 * note never catches a scroll. Insert/Done puts it in the note; X throws the changes away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DrawingOverlay(editor: DocEditor, original: DrawingBlock, onDone: (DrawingBlock) -> Unit, onCancel: () -> Unit) {
    val isNew = editor.doc.blocks.none { it.id == original.id }
    var draft by remember(original.id) { mutableStateOf(original) }
    val redo = remember(original.id) { mutableStateListOf<InkStroke>() }
    val live = remember { mutableStateListOf<Offset>() }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val cs = MaterialTheme.colorScheme
    fun change(d: DrawingBlock) { draft = d }

    Dialog(
        onDismissRequest = { if (draft != original && draft.strokes.isNotEmpty()) onDone(draft) else onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize().testTag(TAG_DRAWING_OVERLAY), color = cs.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel, modifier = Modifier.testTag("drawing-cancel")) { Icon(Icons.Filled.Close, "Discard changes") }
                    Text(if (isNew) "New drawing" else "Drawing", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                        if (zoom > 1.01f || pan != Offset.Zero) {
                            Row(
                                Modifier
                                    .padding(horizontal = 10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(cs.inverseSurface)
                                    .clickable { zoom = 1f; pan = Offset.Zero }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("drawing-zoom-reset"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.ZoomOutMap, null, tint = cs.inverseOnSurface, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${(zoom * 100).roundToInt()}%", color = cs.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    Button(onClick = { onDone(draft) }, modifier = Modifier.testTag("drawing-insert")) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isNew) "Insert" else "Done")
                    }
                }
                // The canvas is as wide as the note's page, so the drawing lands exactly as drawn.
                // One finger draws; two fingers pinch to zoom and drag to move around.
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().clipToBounds().background(cs.surfaceContainerHighest.copy(alpha = 0.5f)),
                ) {
                    val room = maxWidth - 32.dp
                    val width = if (editor.pageWidthDp > 0f) minOf(room, editor.pageWidthDp.dp) else room
                    val baseX = with(LocalDensity.current) { ((maxWidth - width) / 2).toPx() }
                    val baseY = with(LocalDensity.current) { 16.dp.toPx() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag(TAG_DRAWING_CANVAS)
                            .pointerInput(original.id, baseX, baseY, width) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val w = width.toPx().coerceAtLeast(1f)
                                    fun local(p: Offset) = Offset((p.x - baseX - pan.x) / zoom, (p.y - baseY - pan.y) / zoom)
                                    val start = local(down.position)
                                    var drawing = start.x in 0f..w && start.y in 0f..draft.height.dp.toPx()
                                    live.clear()
                                    if (drawing) live.add(start)
                                    down.consume()
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        if (pressed.size >= 2) {
                                            // A second finger: this is a pinch, not a stroke.
                                            drawing = false
                                            live.clear()
                                            val centroid = event.calculateCentroid()
                                            val newZoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                                            val k = newZoom / zoom
                                            val anchor = centroid - Offset(baseX, baseY)
                                            pan = anchor - (anchor - pan) * k + event.calculatePan()
                                            zoom = newZoom
                                            event.changes.forEach { it.consume() }
                                        } else if (drawing) {
                                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                            c.consume()
                                            live.add(local(c.position))
                                        }
                                    }
                                    val pts = live.toList()
                                    live.clear()
                                    if (!drawing || pts.isEmpty()) return@awaitEachGesture
                                    if (editor.eraser) {
                                        val radius = 14.dp.toPx() / zoom
                                        val kept = draft.strokes.filterNot { s -> s.toPx(w, density).any { p -> pts.any { q -> hypot(p.x - q.x, p.y - q.y) < radius } } }
                                        if (kept.size != draft.strokes.size) change(draft.copy(strokes = kept))
                                    } else {
                                        val flat = pts.flatMap { listOf(it.x / w, it.y / density) }
                                        change(draft.copy(strokes = draft.strokes + InkStroke(editor.inkColor, editor.inkWidth, flat)))
                                        redo.clear()
                                    }
                                }
                            },
                    ) {
                        InkCanvas(
                            draft,
                            Modifier
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .offset { IntOffset(baseX.roundToInt(), baseY.roundToInt()) }
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = pan.x
                                    translationY = pan.y
                                }
                                .width(width)
                                .height(draft.height.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                                .background(cs.surface),
                            live = live,
                            liveColor = if (editor.eraser) cs.error.copy(alpha = 0.4f) else Color(editor.inkColor),
                            liveWidth = if (editor.eraser) 28f else editor.inkWidth,
                        )
                    }
                }
                // Tools: ink, pen, canvas.
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ToolGroup(cs.primaryContainer, cs.onPrimaryContainer) {
                        INK_COLORS.forEachIndexed { i, c ->
                            val on = editor.inkColor == c && !editor.eraser
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { editor.inkColor = c; editor.eraser = false }
                                    .testTag("ink-$i"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(if (on) 28.dp else 22.dp)
                                        .background(Color(c), CircleShape)
                                        .border(if (on) 3.dp else 1.dp, if (on) cs.primary else cs.outline, CircleShape)
                                )
                            }
                        }
                    }
                    ToolGroup(cs.secondaryContainer, cs.onSecondaryContainer) {
                        // Pen sizes as dots you can see at a glance.
                        listOf("fine" to 2f, "medium" to 4f, "bold" to 7f, "marker" to 12f).forEach { (name, w) ->
                            val on = editor.inkWidth == w && !editor.eraser
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) LocalContentColor.current.copy(alpha = 0.18f) else Color.Transparent)
                                    .clickable { editor.inkWidth = w; editor.eraser = false }
                                    .testTag("ink-size-$name"),
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.size(w.dp + 3.dp).background(Color(editor.inkColor), CircleShape).border(1.dp, LocalContentColor.current.copy(alpha = 0.3f), CircleShape)) }
                        }
                        ToolButton(Icons.Filled.AutoFixNormal, "Eraser", "ink-eraser", selected = editor.eraser) { editor.eraser = !editor.eraser }
                        ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo stroke", "ink-undo", enabled = draft.strokes.isNotEmpty()) {
                            redo.add(draft.strokes.last())
                            change(draft.copy(strokes = draft.strokes.dropLast(1)))
                        }
                        ToolButton(Icons.AutoMirrored.Filled.Redo, "Redo stroke", "ink-redo", enabled = redo.isNotEmpty()) {
                            change(draft.copy(strokes = draft.strokes + redo.removeAt(redo.lastIndex)))
                        }
                        ToolButton(Icons.Filled.DeleteSweep, "Clear drawing", "ink-clear", enabled = draft.strokes.isNotEmpty()) {
                            redo.clear(); redo.addAll(draft.strokes.reversed())
                            change(draft.copy(strokes = emptyList()))
                        }
                    }
                    ToolGroup(cs.tertiaryContainer, cs.onTertiaryContainer) {
                        ToolMenu(Icons.Filled.Palette, "Canvas colour", "drawing-paper", accent = drawingPaper(draft).takeIf { draft.paper != 0L }) { close ->
                            MenuLabel("Canvas")
                            val card = cs.surfaceContainerLow
                            SwatchGrid(DRAWING_PAPERS.mapIndexed { i, (name, argb) ->
                                Swatch(name, when (argb) { null -> card; 0L -> Color.Transparent; else -> Color(argb) }, "drawing-paper-$i", draft.paper == argb)
                            }) { s ->
                                change(draft.copy(paper = DRAWING_PAPERS[s.tag.removePrefix("drawing-paper-").toInt()].second))
                                close()
                            }
                        }
                        // Tap to step through the guides: plain, dots, grid, lines.
                        ToolButton(
                            when (draft.pattern) { 1 -> Icons.Filled.Apps; 2 -> Icons.Filled.GridOn; 3 -> Icons.Filled.Reorder; else -> Icons.Filled.CropSquare },
                            "Guide: ${DRAWING_PATTERNS[draft.pattern.coerceIn(0, 3)]}",
                            "drawing-pattern",
                        ) { change(draft.copy(pattern = (draft.pattern + 1) % DRAWING_PATTERNS.size)) }
                        ToolButton(Icons.Filled.UnfoldMore, "Taller", "drawing-taller") {
                            change(draft.copy(height = (draft.height + 80).coerceAtMost(1200)))
                        }
                        ToolButton(Icons.Filled.UnfoldLess, "Shorter", "drawing-shorter") {
                            change(draft.copy(height = (draft.height - 80).coerceAtLeast(120)))
                        }
                    }
                }
            }
        }
    }
}
