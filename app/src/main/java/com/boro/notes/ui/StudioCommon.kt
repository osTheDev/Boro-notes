package com.boro.notes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.core.content.FileProvider
import com.boro.notes.studio.FABRICS
import com.boro.notes.studio.Fabric
import com.boro.notes.studio.PadStroke
import com.boro.notes.studio.SketchPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Opens the camera or the photo picker; the photo is copied into the app and [onPhoto] gets its file name. */
class PhotoSource internal constructor(val camera: () -> Unit, val gallery: () -> Unit)

@Composable
fun rememberPhotoSource(onPhoto: (String) -> Unit): PhotoSource {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val import = { uri: Uri -> scope.launch { importImage(context, uri)?.let(onPhoto) }; Unit }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(import) }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pending
        pending = null
        if (ok && path != null) {
            val file = File(path)
            scope.launch {
                importImage(context, Uri.fromFile(file))?.let(onPhoto)
                withContext(Dispatchers.IO) { file.delete() }
            }
        }
    }
    return remember(pick, shoot) {
        PhotoSource(
            camera = {
                runCatching {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                    pending = file.path
                    shoot.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                }
            },
            gallery = { runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
        )
    }
}

/** A photo stored in the app's images, loaded off the main thread. */
@Composable
fun rememberStoredImage(file: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(file) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = if (file == null) null else withContext(Dispatchers.IO) { loadImage(context, file) }
    }
    return bitmap
}

@Composable
fun StoredImage(file: String?, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop, alpha: Float = 1f) {
    val bitmap = rememberStoredImage(file)
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, contentScale = contentScale, alpha = alpha, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {}
    }
}

/** Fills [path] with [fabric]: a soft solid colour with a fine twill grain (and woven stripes for hickory). */
fun DrawScope.fillFabric(path: Path, fabric: Fabric, unit: Float) {
    val base = Color(fabric.base)
    drawPath(path, base)
    clipPath(path) {
        val stripe = fabric.stripe
        if (stripe != null) {
            val pitch = 6f * unit
            var x = 0f
            while (x < size.width) {
                drawRect(Color(stripe), topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(2.2f * unit, size.height))
                x += pitch
            }
        }
        // Twill: fine diagonal ridges, light and dark, so it reads as cloth rather than paint.
        val light = Color.White.copy(alpha = if (base.luminanceApprox() < 0.4f) 0.07f else 0.16f)
        val dark = Color.Black.copy(alpha = 0.06f)
        val step = 3f * unit
        var d = -size.height
        while (d < size.width) {
            drawLine(light, Offset(d, size.height), Offset(d + size.height, 0f), strokeWidth = 0.8f * unit)
            drawLine(dark, Offset(d + step / 2, size.height), Offset(d + step / 2 + size.height, 0f), strokeWidth = 0.6f * unit)
            d += step
        }
    }
}

private fun Color.luminanceApprox() = 0.299f * red + 0.587f * green + 0.114f * blue

/** The stroke as a path in px; the pad is [w] px wide. */
fun padPath(s: PadStroke, w: Float): Path {
    val pts = s.points.chunked(2).mapNotNull { if (it.size == 2) Offset(it[0] * w, it[1] * w) else null }
    return Path().apply {
        if (pts.isEmpty()) return@apply
        moveTo(pts[0].x, pts[0].y)
        if (s.smooth && pts.size > 2) {
            for (i in 1 until pts.size - 1) {
                val mid = Offset((pts[i].x + pts[i + 1].x) / 2, (pts[i].y + pts[i + 1].y) / 2)
                quadraticTo(pts[i].x, pts[i].y, mid.x, mid.y)
            }
            lineTo(pts.last().x, pts.last().y)
        } else {
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        if (s.closed) close()
    }
}

/** Draws a sketch's fills, then its strokes; [unit] is the px size of one pad unit. */
fun DrawScope.drawPadStrokes(strokes: List<PadStroke>, unit: Float) {
    // Widths are dp on a 360 dp wide pad, so they scale with the pad.
    val px = unit / 360f
    strokes.forEach { s ->
        val fabric = s.fill?.let { FABRICS.getOrNull(it) } ?: return@forEach
        if (s.closed) fillFabric(padPath(s, unit), fabric, px * 1.6f)
    }
    strokes.forEach { s ->
        val width = (s.width * px).coerceAtLeast(1f)
        if (s.points.size == 2) {
            drawCircle(Color(s.color), width / 2, Offset(s.points[0] * unit, s.points[1] * unit))
        } else {
            drawPath(padPath(s, unit), Color(s.color), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The sketch paper colour. */
val PAD_PAPER = Color(0xFFFBF8F1)

/**
 * A sketch at [width]: paper, the trace photo (placed, sized and turned as saved) and the strokes,
 * plus [overlay] drawn on top (live strokes in the editor). [photoContent] lets the editor show the
 * photo even while hidden for thumbnails.
 */
@Composable
fun PadSurface(pad: SketchPad, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, overlay: DrawScope.(unit: Float) -> Unit = {}) {
    val unitDp = width / pad.padW
    Box(modifier.size(width, unitDp * pad.padH).clipToBounds().background(PAD_PAPER)) {
        if (pad.photo != null && pad.showPhoto) {
            val bitmap = rememberStoredImage(pad.photo)
            if (bitmap != null) {
                Image(
                    bitmap, null, contentScale = ContentScale.FillWidth, alpha = pad.photoAlpha,
                    modifier = Modifier
                        .offset(unitDp * pad.photoX, unitDp * pad.photoY)
                        .width(unitDp * pad.photoW)
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1))
                        .graphicsLayer { rotationZ = pad.photoRot },
                )
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val unit = size.width / pad.padW
            drawPadStrokes(pad.strokes, unit)
            overlay(unit)
        }
    }
}

/** A small, read-only view of a sketch, fitted into [modifier]'s box. */
@Composable
fun PadThumbnail(pad: SketchPad, modifier: Modifier) {
    BoxWithConstraints(modifier.background(PAD_PAPER), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, maxHeight * (pad.padW / pad.padH))
        PadSurface(pad, w)
    }
}

// ---- The standard tool popup: tap a tool, get one panel with all its settings. ----

/**
 * A toolbar tool that selects itself ([onSelect]) and opens one popup with all its settings. Every
 * tool menu in the app uses this, built from [PopupSection], [ColourDots], [SizeDots], [ChoiceChips],
 * [ToggleRow] and [ActionRow], so menus look and work the same everywhere.
 */
@Composable
internal fun ToolPopup(
    icon: ImageVector,
    description: String,
    tag: String,
    selected: Boolean = false,
    accent: Color? = null,
    onSelect: () -> Unit = {},
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(icon, description, tag, selected = selected || open, accent = accent) { onSelect(); open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.widthIn(min = 240.dp, max = 320.dp).padding(bottom = 4.dp).testTag("$tag-popup")) {
                Text(description, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp))
                content { open = false }
            }
        }
    }
}

/** A titled section inside a tool popup. */
@Composable
internal fun PopupSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColourDots(colours: List<Long>, selected: Long?, tagPrefix: String, onPick: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        colours.forEachIndexed { i, c ->
            val on = c == selected
            Box(Modifier.size(36.dp).clip(CircleShape).clickable { onPick(c) }.testTag("$tagPrefix-$i"), contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (on) 30.dp else 24.dp).background(Color(c), CircleShape).border(if (on) 3.dp else 1.dp, if (on) cs.primary else cs.outline, CircleShape))
            }
        }
    }
}

/** Sizes as dots you can see at a glance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SizeDots(sizes: List<Pair<String, Float>>, selected: Float, colour: Color, tagPrefix: String, onPick: (Float) -> Unit) {
    val cs = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sizes.forEach { (name, w) ->
            val on = w == selected
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) cs.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPick(w) }.padding(horizontal = 8.dp, vertical = 4.dp).testTag("$tagPrefix-${name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size((w.coerceAtMost(22f)).dp + 2.dp).background(colour, CircleShape).border(1.dp, cs.outline, CircleShape))
                }
                Text(name, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChoiceChips(options: List<String>, selected: Int, tagPrefix: String, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = i == selected, onClick = { onPick(i) }, label = { Text(o) }, modifier = Modifier.testTag("$tagPrefix-$i"))
        }
    }
}

@Composable
internal fun ToggleRow(label: String, hint: String?, checked: Boolean, tag: String, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(horizontal = 16.dp, vertical = 2.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
internal fun ActionRow(icon: ImageVector, label: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag))
}
