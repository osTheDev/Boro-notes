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
 * One layer of a sketch, like tracing paper: rough it out on one, then perfect it on another.
 * [alpha] fades the whole layer; hidden layers aren't drawn.
 */
@Serializable
data class PadLayer(
    val id: String,
    val name: String = "Layer",
    val strokes: List<PadStroke> = emptyList(),
    val alpha: Float = 1f,
    val visible: Boolean = true,
)

/**
 * A loose sketch in [layers] (bottom first), optionally traced over a faded photo. Units: 1.0 is
 * the width of a new pad, which starts [padW] by [padH] and can grow in any direction.
 */
@Serializable
data class SketchPad(
    val layers: List<PadLayer> = listOf(PadLayer("1", "Layer 1")),
    /** The layer new marks go on. */
    val active: Int = 0,
    /** Strokes saved before layers existed; [StudioJson.pad] moves them into the first layer. */
    @kotlinx.serialization.SerialName("strokes")
    val legacyStrokes: List<PadStroke> = emptyList(),
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
    /** Every stroke on every layer, bottom first. */
    val strokes: List<PadStroke> get() = layers.flatMap { it.strokes }
    val activeIndex: Int get() = active.coerceIn(0, (layers.size - 1).coerceAtLeast(0))
    val activeLayer: PadLayer get() = layers.getOrElse(activeIndex) { PadLayer("1", "Layer 1") }
    val isEmpty get() = layers.all { it.strokes.isEmpty() } && photo == null
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
    fun pad(s: String): SketchPad = runCatching { json.decodeFromString<SketchPad>(s) }.getOrDefault(SketchPad()).let { p ->
        when {
            p.legacyStrokes.isNotEmpty() -> p.copy(legacyStrokes = emptyList(), layers = listOf(PadLayer("1", "Layer 1", p.legacyStrokes)), active = 0)
            p.layers.isEmpty() -> p.copy(layers = listOf(PadLayer("1", "Layer 1")), active = 0)
            else -> p
        }
    }
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
            layers = layers.map { l -> l.copy(strokes = l.strokes.map { s -> s.copy(points = s.points.mapIndexed { i, v -> if (i % 2 == 0) v + dx else v + dy }) }) },
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

    // ---- Layers ----

    /** Changes the strokes of the active layer (showing it, so you see what you did). */
    fun editActive(pad: SketchPad, change: (List<PadStroke>) -> List<PadStroke>): SketchPad {
        val i = pad.activeIndex
        val layers = pad.layers.ifEmpty { listOf(PadLayer("1", "Layer 1")) }
        return pad.copy(layers = layers.mapIndexed { j, l -> if (j == i) l.copy(strokes = change(l.strokes), visible = true) else l }, active = i)
    }

    fun updateLayer(pad: SketchPad, index: Int, change: (PadLayer) -> PadLayer): SketchPad =
        pad.copy(layers = pad.layers.mapIndexed { j, l -> if (j == index) change(l) else l })

    /** A new empty layer just above the active one, which becomes active. */
    fun addLayer(pad: SketchPad): SketchPad {
        val n = (pad.layers.mapNotNull { it.name.removePrefix("Layer ").toIntOrNull() }.maxOrNull() ?: pad.layers.size) + 1
        val id = ((pad.layers.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()
        val at = (pad.activeIndex + 1).coerceAtMost(pad.layers.size)
        val layers = pad.layers.toMutableList().apply { add(at, PadLayer(id, "Layer $n")) }
        return pad.copy(layers = layers, active = at)
    }

    /**
     * The fill tool at [at] on the active layer: recolours the closed shape there, or else fills the
     * area the lines enclose (this layer's lines first, then every shown layer's) as a fill of its
     * own under the lines. Returns [pad] unchanged when there's nothing closed to fill.
     */
    fun fill(pad: SketchPad, at: Pt, fabric: Int): SketchPad {
        val mine = pad.activeLayer.strokes
        val target = fillTarget(mine, at)
        if (target != null) return editActive(pad) { list -> list.mapIndexed { i, s -> if (i == target) s.copy(fill = fabric) else s } }
        val poly = floodFill(mine, at, pad.padW, pad.padH)
            ?: floodFill(pad.layers.filter { it.visible }.flatMap { it.strokes }, at, pad.padW, pad.padH)
            ?: return pad
        val colour = FABRICS.getOrNull(fabric)?.base ?: 0xFF000000
        val area = PadStroke(colour, 0f, poly.flatMap { listOf(it.x, it.y) }, closed = true, smooth = false, fill = fabric)
        return editActive(pad) { listOf(area) + it }
    }

    /** Removes a layer (there is always at least one). */
    fun deleteLayer(pad: SketchPad, index: Int): SketchPad {
        if (pad.layers.size <= 1 || index !in pad.layers.indices) return pad
        val layers = pad.layers.filterIndexed { j, _ -> j != index }
        val active = when {
            pad.activeIndex > index -> pad.activeIndex - 1
            pad.activeIndex == index -> (index - 1).coerceAtLeast(0)
            else -> pad.activeIndex
        }
        return pad.copy(layers = layers, active = active.coerceIn(0, layers.size - 1))
    }

    /** Moves a layer one step up (towards the top) or down; the active layer follows it. */
    fun moveLayer(pad: SketchPad, index: Int, up: Boolean): SketchPad {
        val to = if (up) index + 1 else index - 1
        if (index !in pad.layers.indices || to !in pad.layers.indices) return pad
        val layers = pad.layers.toMutableList().apply { add(to, removeAt(index)) }
        val active = when (pad.activeIndex) {
            index -> to
            to -> index
            else -> pad.activeIndex
        }
        return pad.copy(layers = layers, active = active)
    }

    // ---- Eraser ----

    /**
     * The smudge eraser: rubs out only what the eraser circle passes over, splitting lines where it
     * cuts them. A filled shape keeps its fill when its outline is cut; a fill is rubbed out whole.
     */
    fun eraseArea(strokes: List<PadStroke>, path: List<Pt>, radius: Float): List<PadStroke> = strokes.flatMap { s ->
        val pts = s.pts()
        fun hit(p: Pt) = path.any { it.dist(p) < radius }
        when {
            pts.isEmpty() -> emptyList()
            pts.size == 1 -> if (hit(pts[0])) emptyList() else listOf(s)
            // A fill on its own (from the fill tool) has no line to cut: it goes if you rub inside it.
            s.width <= 0f -> if (path.any { contains(pts, it) } || pts.any(::hit)) emptyList() else listOf(s)
            else -> {
                val ring = if (s.closed) pts + pts.first() else pts
                val dense = densify(ring, radius / 3f)
                if (dense.none(::hit)) {
                    // Rubbing inside a filled shape takes the fill and leaves the line.
                    if (s.closed && s.fill != null && path.any { contains(pts, it) }) listOf(s.copy(fill = null)) else listOf(s)
                } else {
                    val runs = mutableListOf<MutableList<Pt>>()
                    var cur: MutableList<Pt>? = null
                    for (p in dense) {
                        if (hit(p)) cur = null else {
                            if (cur == null) { cur = mutableListOf(); runs += cur }
                            cur += p
                        }
                    }
                    // A closed line cut once stays one piece around the seam.
                    if (s.closed && runs.size >= 2 && !hit(dense.first()) && !hit(dense.last())) {
                        val last = runs.removeAt(runs.lastIndex)
                        runs[0] = (last + runs[0]).toMutableList()
                    }
                    val keptFill = if (s.closed && s.fill != null) listOf(s.copy(width = 0f)) else emptyList()
                    keptFill + runs.filter { it.size >= 2 }.map { r ->
                        s.copy(points = Geometry.rdp(r, 0.0008f).flatMap { listOf(it.x, it.y) }, closed = false, fill = null)
                    }
                }
            }
        }
    }

    private fun densify(pts: List<Pt>, step: Float): List<Pt> {
        if (pts.size < 2 || step <= 0f) return pts
        val out = ArrayList<Pt>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val n = kotlin.math.ceil(a.dist(b) / step).toInt().coerceAtLeast(1)
            for (k in 0 until n) out += a + (b - a) * (k.toFloat() / n)
        }
        out += pts.last()
        return out
    }

    // ---- Fill ----

    /**
     * Fills the area around [at] that the lines enclose, like a paint bucket: it works across
     * several strokes and jumps small gaps ([gap], pad units), so a hand-drawn pocket fills even when
     * it isn't one perfect closed line. Returns the area's outline, or null if it leaks to the edge.
     */
    fun floodFill(strokes: List<PadStroke>, at: Pt, padW: Float, padH: Float, gap: Float = 0.012f): List<Pt>? {
        // About 250 cells across a page, fewer on a big pad so it stays quick.
        var cell = 0.004f
        while ((padW / cell) * (padH / cell) > 400_000f) cell *= 1.25f
        val cols = kotlin.math.ceil(padW / cell).toInt() + 2
        val rows = kotlin.math.ceil(padH / cell).toInt() + 2
        fun cx(x: Float) = (x / cell).toInt() + 1
        fun cy(y: Float) = (y / cell).toInt() + 1
        val wall = BooleanArray(cols * rows)
        val gapCells = gap / cell
        for (s in strokes) {
            if (s.width <= 0f) continue
            val pts = s.pts()
            if (pts.isEmpty()) continue
            val r = (s.width / 360f / 2f) / cell + gapCells
            val ri = kotlin.math.ceil(r).toInt()
            val ring = if (s.closed && pts.size > 2) pts + pts.first() else pts
            for (p in densify(ring, cell / 2f)) {
                val px = cx(p.x)
                val py = cy(p.y)
                for (dy in -ri..ri) for (dx in -ri..ri) {
                    if (dx * dx + dy * dy > r * r) continue
                    val x = px + dx
                    val y = py + dy
                    if (x in 0 until cols && y in 0 until rows) wall[y * cols + x] = true
                }
            }
        }
        val sx = cx(at.x)
        val sy = cy(at.y)
        if (sx !in 1 until cols - 1 || sy !in 1 until rows - 1 || wall[sy * cols + sx]) return null
        // Flood the open cells; touching the border means the shape isn't closed.
        val region = BooleanArray(cols * rows)
        val queue = IntArray(cols * rows)
        var head = 0
        var tail = 0
        queue[tail++] = sy * cols + sx
        region[sy * cols + sx] = true
        while (head < tail) {
            val c = queue[head++]
            val x = c % cols
            val y = c / cols
            if (x <= 0 || y <= 0 || x >= cols - 1 || y >= rows - 1) return null
            for (n in intArrayOf(c - 1, c + 1, c - cols, c + cols)) {
                if (!region[n] && !wall[n]) { region[n] = true; queue[tail++] = n }
            }
        }
        // Grow back under the lines (by the gap allowance) so the fill meets the ink.
        val grow = kotlin.math.ceil(gapCells).toInt() + 1
        val filled = region.copyOf()
        for (c in 0 until cols * rows) {
            if (!region[c]) continue
            val x = c % cols
            val y = c / cols
            for (dy in -grow..grow) for (dx in -grow..grow) {
                if (dx * dx + dy * dy > grow * grow) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 1 until cols - 1 && ny in 1 until rows - 1) filled[ny * cols + nx] = true
            }
        }
        val outline = traceOutline(filled, cols, rows) ?: return null
        val poly = outline.map { (x, y) -> Pt((x - 1 + 0.5f) * cell, (y - 1 + 0.5f) * cell) }
        val simple = Geometry.rdp(poly + poly.first(), cell * 0.75f).dropLast(1)
        return if (simple.size >= 3) simple else null
    }

    /** The outer edge of the cells set in [mask], in order (Moore neighbour tracing). */
    private fun traceOutline(mask: BooleanArray, cols: Int, rows: Int): List<Pair<Int, Int>>? {
        val start = mask.indexOfFirst { it }.takeIf { it >= 0 } ?: return null
        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        fun on(x: Int, y: Int) = x in 0 until cols && y in 0 until rows && mask[y * cols + x]
        val sx = start % cols
        val sy = start / cols
        val out = ArrayList<Pair<Int, Int>>()
        var x = sx
        var y = sy
        var back = 4 // came from the west, which is empty
        val limit = cols * rows * 4
        var steps = 0
        var first: Pair<Int, Int>? = null
        while (steps++ < limit) {
            out += x to y
            var found = -1
            for (k in 1..8) {
                val d = (back + k) % 8
                if (on(x + dx[d], y + dy[d])) { found = d; break }
            }
            if (found < 0) break // a single cell
            val prev = (found + 7) % 8
            val nx = x + dx[found]
            val ny = y + dy[found]
            // The new backtrack points from the next cell to the last empty cell checked.
            val bx = x + dx[prev] - nx
            val by = y + dy[prev] - ny
            back = (0 until 8).first { dx[it] == bx && dy[it] == by }
            if (x == sx && y == sy) {
                if (first == null) first = nx to ny else if (first == nx to ny) break
            }
            x = nx
            y = ny
        }
        if (out.size > 1 && out.last() == out.first()) out.removeAt(out.lastIndex)
        return out
    }

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
