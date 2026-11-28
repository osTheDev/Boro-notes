package com.boro.notes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

/** Draws a sketch's fills, then its strokes, on a pad [size.width] px wide. */
fun DrawScope.drawPadStrokes(strokes: List<PadStroke>) {
    val w = size.width
    val scale = w / 360f
    strokes.forEach { s ->
        val fabric = s.fill?.let { FABRICS.getOrNull(it) } ?: return@forEach
        if (s.closed) fillFabric(padPath(s, w), fabric, density * scale)
    }
    strokes.forEach { s ->
        val width = (s.width * density * scale).coerceAtLeast(1f)
        if (s.points.size == 2) {
            drawCircle(Color(s.color), width / 2, Offset(s.points[0] * w, s.points[1] * w))
        } else {
            drawPath(padPath(s, w), Color(s.color), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A small, read-only view of a sketch (photo underlay included). */
@Composable
fun PadThumbnail(pad: SketchPad, modifier: Modifier) {
    Box(modifier.background(Color(0xFFFBF8F1))) {
        if (pad.photo != null && pad.showPhoto) {
            StoredImage(pad.photo, Modifier.matchParentSize(), ContentScale.Crop, alpha = pad.photoAlpha)
        }
        Canvas(Modifier.matchParentSize()) { drawPadStrokes(pad.strokes) }
    }
}
