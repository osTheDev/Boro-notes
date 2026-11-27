package com.boro.notes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.boro.notes.notes.DrawingBlock
import com.boro.notes.notes.ImageBlock
import com.boro.notes.notes.InkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max

const val TAG_NOTE_IMAGE = "noteImage"
const val TAG_NOTE_DRAWING = "noteDrawing"

/** Ink colours for drawings: graphite, indigo, rust, sage, ochre, plum, red, white. */
val INK_COLORS = listOf(
    0xFF2B2B2B, 0xFF2E4A7D, 0xFFB5562B, 0xFF5E8259, 0xFFC08A1E, 0xFF7A3E6E, 0xFFD32F2F, 0xFFFFFFFF,
)

private fun imagesDir(context: Context) = File(context.filesDir, "images").apply { mkdirs() }

/**
 * Copies a picked photo into the app (scaled to at most 2048 px, JPEG) so notes keep it even if the
 * original is deleted. Returns the stored file name, or null if it couldn't be read.
 */
suspend fun importImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching null
        val name = "${UUID.randomUUID()}.jpg"
        File(imagesDir(context), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        name
    }.getOrNull()
}

private fun loadImage(context: Context, name: String): ImageBitmap? = runCatching {
    val file = File(imagesDir(context), name)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

/** A photo with a caption. Tap it to select (resize, move, delete from the tools row). */
@Composable
internal fun ImageBlockView(editor: DocEditor, block: ImageBlock) {
    val context = LocalContext.current
    var bitmap by remember(block.file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(block.file) { bitmap = withContext(Dispatchers.IO) { loadImage(context, block.file) } }
    val selected = editor.focused == block.id
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag(TAG_NOTE_IMAGE), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth(block.width.coerceIn(0.3f, 1f))
                .clip(RoundedCornerShape(12.dp))
                .border(if (selected) 3.dp else 0.dp, if (selected) cs.primary else Color.Transparent, RoundedCornerShape(12.dp))
                .clickable(enabled = !editor.readOnly) { editor.focused = block.id },
        ) {
            val b = bitmap
            if (b != null) {
                Image(b, contentDescription = block.caption.ifBlank { "Photo" }, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            } else {
                Box(Modifier.fillMaxWidth().height(160.dp).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("Photo", color = cs.onSurfaceVariant)
                }
            }
        }
        Caption(editor, block.id, block.caption) { editor.update(editor.doc.replace(editor.latest(block).copy(caption = it))) }
    }
}

@Composable
private fun Caption(editor: DocEditor, id: String, value: String, onChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        cursorBrush = SolidColor(cs.primary),
        singleLine = true,
        readOnly = editor.readOnly,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (value.isEmpty() && !editor.readOnly) Text("Add a caption", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(alpha = 0.6f))
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

/**
 * A drawing area. While it's selected, finger strokes draw (or erase) instead of scrolling;
 * tap another block or Done to go back to scrolling.
 */
@Composable
internal fun DrawingBlockView(editor: DocEditor, block: DrawingBlock) {
    val cs = MaterialTheme.colorScheme
    val active = editor.focused == block.id
    // The stroke being drawn right now, in px.
    val live = remember { mutableStateListOf<Offset>() }
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(block.height.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cs.surfaceContainerLow)
                .border(if (active) 3.dp else 1.dp, if (active) cs.primary else cs.outlineVariant, RoundedCornerShape(12.dp))
                .testTag(TAG_NOTE_DRAWING)
                .pointerInput(active, block.id, editor.readOnly) {
                    if (editor.readOnly) return@pointerInput
                    if (!active) {
                        awaitEachGesture {
                            awaitFirstDown()
                            editor.focused = block.id
                        }
                        return@pointerInput
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        live.clear()
                        live.add(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            live.add(change.position)
                        }
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val pts = live.toList()
                        live.clear()
                        val current = editor.latest(block)
                        if (editor.eraser) {
                            // Remove any stroke the finger passed over.
                            val radius = 14.dp.toPx()
                            val kept = current.strokes.filterNot { s -> s.toPx(w, density).any { p -> pts.any { q -> hypot(p.x - q.x, p.y - q.y) < radius } } }
                            if (kept.size != current.strokes.size) editor.update(editor.doc.replace(current.copy(strokes = kept)))
                        } else if (pts.isNotEmpty()) {
                            val flat = pts.flatMap { listOf(it.x / w, it.y / density) }
                            val stroke = InkStroke(editor.inkColor, editor.inkWidth, flat)
                            editor.update(editor.doc.replace(current.copy(strokes = current.strokes + stroke)))
                        }
                    }
                }
        ) {
            val w = size.width
            block.strokes.forEach { s -> drawInk(s.toPx(w, density), Color(s.color), s.width.dp.toPx()) }
            if (live.isNotEmpty()) {
                drawInk(live, if (editor.eraser) cs.error.copy(alpha = 0.4f) else Color(editor.inkColor), (if (editor.eraser) 28f else editor.inkWidth).dp.toPx())
            }
            if (block.strokes.isEmpty() && live.isEmpty()) {
                // Hint dots so the empty area reads as a sketch pad.
                val step = 24.dp.toPx()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) { drawCircle(cs.outlineVariant, 1.2f * density, Offset(x, y)); x += step }
                    y += step
                }
            }
        }
        if (!active && block.strokes.isEmpty() && !editor.readOnly) {
            Text(
                "Tap to draw",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp),
            )
        }
        Caption(editor, block.id, block.caption) { editor.update(editor.doc.replace(editor.latest(block).copy(caption = it))) }
    }
}

private fun InkStroke.toPx(width: Float, density: Float): List<Offset> =
    points.chunked(2).mapNotNull { if (it.size == 2) Offset(it[0] * width, it[1] * density) else null }

/** A smooth stroke through [pts] (quadratic curves between midpoints). */
private fun DrawScope.drawInk(pts: List<Offset>, color: Color, width: Float) {
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        drawCircle(color, width / 2, pts[0])
        return
    }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size - 1) {
            val mid = Offset((pts[i].x + pts[i + 1].x) / 2, (pts[i].y + pts[i + 1].y) / 2)
            quadraticTo(pts[i].x, pts[i].y, mid.x, mid.y)
        }
        lineTo(pts.last().x, pts.last().y)
    }
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
