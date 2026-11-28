package com.boro.notes.studio

import com.boro.notes.sketch.DrawOptions
import com.boro.notes.sketch.Geometry
import com.boro.notes.sketch.Pt
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Sketch pads are 3 wide by 4 tall; coordinates are fractions of the width (so y runs 0..[PAD_HEIGHT]). */
const val PAD_HEIGHT = 4f / 3f

/** Grid units across the pad, so the sketch engine's tolerances (made for ~1 inch squares) fit a finger. */
private const val PAD_UNITS = 24f

/**
 * One mark on a sketch pad. [points] are x,y pairs as fractions of the pad width. [width] is in dp
 * on a 360 dp wide pad (it scales with the pad). [fill] is an index into [FABRICS].
 */
@Serializable
data class PadStroke(
    val color: Long,
    val width: Float,
    val points: List<Float>,
    val closed: Boolean = false,
    /** Drawn as a smooth curve through the points (freehand), or straight between them. */
    val smooth: Boolean = true,
    val fill: Int? = null,
) {
    fun pts(): List<Pt> = points.chunked(2).mapNotNull { if (it.size == 2) Pt(it[0], it[1]) else null }
}

/**
 * A loose sketch, optionally traced over a faded photo. Units: 1.0 is the width of a new pad, which
 * starts [padW] by [padH] and can grow in any direction.
 */
@Serializable
data class SketchPad(
    val strokes: List<PadStroke> = emptyList(),
    /** Photo to trace over (file name in filesDir/images). */
    val photo: String? = null,
    val photoAlpha: Float = 0.45f,
    val showPhoto: Boolean = true,
    /** The photo's top-left corner and width (pad units) and its turn in degrees (about its centre). */
    val photoX: Float = 0f,
    val photoY: Float = 0f,
    val photoW: Float = 1f,
    val photoRot: Float = 0f,
    val padW: Float = 1f,
    val padH: Float = PAD_HEIGHT,
) {
    val isEmpty get() = strokes.isEmpty() && photo == null
}

/** A side of the pad, for growing it. */
enum class PadSide { LEFT, TOP, RIGHT, BOTTOM }

@Serializable
enum class CollageKind { PHOTO, IDEA, DESIGN, TEXT }

/**
 * One thing on a collage. Position and width are fractions of the board width (the board is
 * square); height follows the content. [rot] is in degrees. List order is stacking order.
 */
@Serializable
data class CollageItem(
    val id: String,
    val kind: CollageKind,
    /** Photo file name, or the idea / design id. */
    val ref: String = "",
    val text: String = "",
    val x: Float = 0.1f,
    val y: Float = 0.1f,
    val w: Float = 0.4f,
    val rot: Float = 0f,
    /** Text note colour (ARGB). */
    val color: Long = 0xFFF3E3A6,
)

@Serializable
data class Collage(val items: List<CollageItem> = emptyList(), val paper: Long? = null) {
    val isEmpty get() = items.isEmpty()
}

object StudioJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun pad(s: String): SketchPad = runCatching { json.decodeFromString<SketchPad>(s) }.getOrDefault(SketchPad())
    fun collage(s: String): Collage = runCatching { json.decodeFromString<Collage>(s) }.getOrDefault(Collage())
    fun encode(p: SketchPad) = json.encodeToString(p)
    fun encode(c: Collage) = json.encodeToString(c)
}

/** Quick shapes dragged corner to corner. */
enum class PadShape { LINE, BOX, OVAL, TRIANGLE }

object Pad {
    /** The mirror line runs down the middle of a pad [padW] wide. */
    fun mirror(s: PadStroke, padW: Float = 1f): PadStroke =
        s.copy(points = s.points.chunked(2).flatMap { if (it.size == 2) listOf(padW - it[0], it[1]) else emptyList() })

    /** Makes the pad bigger by [amount] on [side]; growing left or up shifts the drawing so it stays put. */
    fun grow(pad: SketchPad, side: PadSide, amount: Float = 0.5f): SketchPad {
        val dx = if (side == PadSide.LEFT) amount else 0f
        val dy = if (side == PadSide.TOP) amount else 0f
        return pad.shifted(dx, dy).copy(
            padW = pad.padW + if (side == PadSide.LEFT || side == PadSide.RIGHT) amount else 0f,
            padH = pad.padH + if (side == PadSide.TOP || side == PadSide.BOTTOM) amount else 0f,
        )
    }

    /** Shrinks the pad to the drawing plus a small margin (never smaller than half a pad). */
    fun trim(pad: SketchPad, margin: Float = 0.06f): SketchPad {
        val pts = pad.strokes.flatMap { it.pts() }
        if (pts.isEmpty()) return pad
        val minX = pts.minOf { it.x } - margin
        val minY = pts.minOf { it.y } - margin
        val w = maxOf(0.5f, pts.maxOf { it.x } + margin - minX)
        val h = maxOf(0.5f, pts.maxOf { it.y } + margin - minY)
        return pad.shifted(-minX, -minY).copy(padW = w, padH = h)
    }

    private fun SketchPad.shifted(dx: Float, dy: Float): SketchPad =
        if (dx == 0f && dy == 0f) this else copy(
            strokes = strokes.map { s -> s.copy(points = s.points.mapIndexed { i, v -> if (i % 2 == 0) v + dx else v + dy }) },
            photoX = photoX + dx,
            photoY = photoY + dy,
        )

    /**
     * Auto-level: turns each straight run so its angle is a multiple of [stepDeg] (90 keeps to right
     * angles, 45 adds diagonals, 15 is finer). Curves keep their shape and just move along.
     */
    fun level(points: List<Pt>, corners: List<Boolean>, stepDeg: Int): List<Pt> {
        if (stepDeg <= 0 || points.size < 2) return points
        val step = Math.toRadians(stepDeg.toDouble())
        val out = ArrayList<Pt>(points.size)
        out += points[0]
        var delta = Pt(0f, 0f)
        for (i in 1 until points.size) {
            val moved = points[i] + delta
            if (corners.getOrElse(i - 1) { true } && corners.getOrElse(i) { true }) {
                val v = points[i] - points[i - 1]
                val len = v.length()
                val angle = Math.round(kotlin.math.atan2(v.y.toDouble(), v.x.toDouble()) / step) * step
                val snapped = Pt(out[i - 1].x + (len * cos(angle)).toFloat(), out[i - 1].y + (len * sin(angle)).toFloat())
                delta = snapped - points[i]
                out += snapped
            } else {
                out += moved
            }
        }
        return out
    }

    /**
     * Turns a raw finger stroke (pad fractions) into a stroke. With [straighten] it goes through the
     * sketch engine: straight runs become lines, curves become smooth, and a loop closes.
     */
    fun stroke(raw: List<Pt>, color: Long, width: Float, straighten: Boolean, levelDeg: Int = 0): PadStroke? {
        if (raw.isEmpty()) return null
        if (raw.size == 1 || Geometry.pathLength(raw) < 0.004f) {
            // A tap leaves a dot.
            return PadStroke(color, width, listOf(raw[0].x, raw[0].y))
        }
        if (straighten) {
            val units = raw.map { it * PAD_UNITS }
            val shape = Geometry.recognize(units, emptyList(), DrawOptions(snapToGrid = false, straighten = true))
            if (shape != null) {
                val levelled = shape.copy(points = level(shape.points, shape.corners, levelDeg))
                val outline = Geometry.outline(levelled).map { it * (1f / PAD_UNITS) }
                return PadStroke(color, width, outline.flatMap { listOf(it.x, it.y) }, closed = shape.closed, smooth = false)
            }
        }
        val length = Geometry.pathLength(raw)
        val closed = length > 0.15f && raw.first().dist(raw.last()) < maxOf(0.03f, 0.08f * length)
        val pts = Geometry.rdp(raw, 0.0015f)
        return PadStroke(color, width, pts.flatMap { listOf(it.x, it.y) }, closed = closed)
    }

    fun shape(kind: PadShape, from: Pt, to: Pt, color: Long, width: Float, levelDeg: Int = 0): PadStroke? {
        if (from.dist(to) < 0.01f) return null
        val pts: List<Pt> = when (kind) {
            PadShape.LINE -> level(listOf(from, to), listOf(true, true), levelDeg)
            PadShape.BOX -> listOf(from, Pt(to.x, from.y), to, Pt(from.x, to.y))
            PadShape.TRIANGLE -> listOf(Pt((from.x + to.x) / 2, from.y), Pt(to.x, to.y), Pt(from.x, to.y))
            PadShape.OVAL -> {
                val c = Pt((from.x + to.x) / 2, (from.y + to.y) / 2)
                val rx = kotlin.math.abs(to.x - from.x) / 2
                val ry = kotlin.math.abs(to.y - from.y) / 2
                (0 until 48).map { i ->
                    val a = 2 * PI * i / 48
                    Pt(c.x + rx * cos(a).toFloat(), c.y + ry * sin(a).toFloat())
                }
            }
        }
        return PadStroke(
            color, width, pts.flatMap { listOf(it.x, it.y) },
            closed = kind != PadShape.LINE,
            smooth = false,
        )
    }

    /** The topmost closed stroke under [p], for filling. */
    fun fillTarget(strokes: List<PadStroke>, p: Pt): Int? =
        strokes.indices.reversed().firstOrNull { i -> strokes[i].closed && contains(strokes[i].pts(), p) }

    /** Strokes passing within [radius] of any point of [path] (for the eraser). */
    fun touched(strokes: List<PadStroke>, path: List<Pt>, radius: Float): Set<Int> =
        strokes.indices.filter { i ->
            val pts = strokes[i].pts()
            when {
                pts.size == 1 -> path.any { it.dist(pts[0]) < radius }
                else -> path.any { q ->
                    val segs = pts.zipWithNext() + if (strokes[i].closed) listOf(pts.last() to pts.first()) else emptyList()
                    segs.any { (a, b) -> Geometry.distToSegment(q, a, b) < radius }
                } || (strokes[i].closed && strokes[i].fill != null && path.any { contains(pts, it) })
            }
        }.toSet()

    fun contains(poly: List<Pt>, p: Pt): Boolean {
        if (poly.size < 3) return false
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    fun distance(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
}

/** A soft fabric colour: [base], with an optional woven stripe (hickory). */
data class Fabric(val name: String, val base: Long, val stripe: Long? = null)

/** Fabric fills shared by sketches (and designs later): solid, slightly soft colours with a twill grain. */
val FABRICS = listOf(
    Fabric("Raw indigo", 0xFF1E2A47),
    Fabric("Faded indigo", 0xFF5D7398),
    Fabric("Black", 0xFF2B2B2F),
    Fabric("Ecru", 0xFFE6DCC4),
    Fabric("Duck brown", 0xFFA4744A),
    Fabric("Hickory stripe", 0xFFE4DDCC, stripe = 0xFF2C3654),
    Fabric("Olive", 0xFF66683F),
    Fabric("Rust", 0xFF9A4B2E),
    Fabric("Grey marl", 0xFF8E8E8C),
    Fabric("Sashiko white", 0xFFF4F1EA),
)
