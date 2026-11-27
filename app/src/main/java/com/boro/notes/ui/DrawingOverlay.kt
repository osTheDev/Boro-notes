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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
                    Button(onClick = { onDone(draft) }, modifier = Modifier.testTag("drawing-insert")) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isNew) "Insert" else "Done")
                    }
                }
                // The canvas is as wide as the note's page, so the drawing lands exactly as drawn.
                BoxWithConstraints(
                    Modifier.weight(1f).fillMaxWidth().background(cs.surfaceContainerHighest.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val room = maxWidth - 32.dp
                    val width = if (editor.pageWidthDp > 0f) minOf(room, editor.pageWidthDp.dp) else room
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        InkCanvas(
                            draft,
                            Modifier
                                .width(width)
                                .height(draft.height.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                                .background(cs.surface)
                                .testTag(TAG_DRAWING_CANVAS)
                                .pointerInput(original.id) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown()
                                        down.consume()
                                        live.clear()
                                        live.add(down.position)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!c.pressed) break
                                            c.consume()
                                            live.add(c.position)
                                        }
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val pts = live.toList()
                                        live.clear()
                                        if (editor.eraser) {
                                            val radius = 14.dp.toPx()
                                            val kept = draft.strokes.filterNot { s -> s.toPx(w, density).any { p -> pts.any { q -> hypot(p.x - q.x, p.y - q.y) < radius } } }
                                            if (kept.size != draft.strokes.size) change(draft.copy(strokes = kept))
                                        } else if (pts.isNotEmpty()) {
                                            val flat = pts.flatMap { listOf(it.x / w, it.y / density) }
                                            change(draft.copy(strokes = draft.strokes + InkStroke(editor.inkColor, editor.inkWidth, flat)))
                                            redo.clear()
                                        }
                                    }
                                },
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
                        ToolMenu(Icons.Filled.LineWeight, "Pen size", "ink-size") { close ->
                            MenuLabel("Pen size")
                            listOf("Fine" to 2f, "Medium" to 4f, "Bold" to 7f, "Marker" to 12f).forEach { (name, w) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    leadingIcon = { Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(w.dp + 2.dp).background(Color(editor.inkColor), CircleShape)) } },
                                    trailingIcon = { if (editor.inkWidth == w) Icon(Icons.Filled.Check, null) },
                                    onClick = { editor.inkWidth = w; editor.eraser = false; close() },
                                    modifier = Modifier.testTag("ink-size-${name.lowercase()}"),
                                )
                            }
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
                        ToolMenu(Icons.Filled.GridOn, "Guide", "drawing-pattern") { close ->
                            MenuLabel("Guide")
                            DRAWING_PATTERNS.forEachIndexed { i, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    trailingIcon = { if (draft.pattern == i) Icon(Icons.Filled.Check, null) },
                                    onClick = { change(draft.copy(pattern = i)); close() },
                                    modifier = Modifier.testTag("drawing-pattern-$i"),
                                )
                            }
                        }
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
