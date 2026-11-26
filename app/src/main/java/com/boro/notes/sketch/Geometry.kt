package com.boro.notes.sketch

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A point in grid units: 1.0 is one grid square. */
@Serializable
data class Pt(val x: Float, val y: Float) {
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
    fun dist(o: Pt) = hypot(x - o.x, y - o.y)
    fun length() = hypot(x, y)
}

/**
 * One drawn line, open or closed. Corner points are joined by straight segments;
 * runs of non-corner points are drawn as a smooth curve through them.
 */
@Serializable
data class Shape(
    val points: List<Pt>,
    val corners: List<Boolean>,
    val closed: Boolean = false,
    val fill: Int? = null,
    /** False for a fill-only region made from separate lines that enclose an area. */
    val stroke: Boolean = true,
    /** Shapes sharing a group id select and move together. */
    val group: Long? = null,
    val style: LineStyle = LineStyle.SOLID,
    /** Quilting/stitch pattern drawn inside a closed shape. */
    val pattern: FillPattern? = null,
    /** Hardware drawn at the shape's single point. */
    val stamp: Stamp? = null,
)

@Serializable
enum class LineStyle { SOLID, STITCH }

@Serializable
enum class FillPattern(val label: String) {
    DIAMOND("Diamond"),
    LINES("Lines"),
    CROSSHATCH("Crosshatch"),
}

@Serializable
enum class Stamp(val label: String, val radius: Float) {
    RIVET("Rivet", 0.3f),
    SNAP("Snap", 0.5f),
    BUTTON("Button", 0.65f),
}

data class DrawOptions(
    val snapToGrid: Boolean = true,
    val straighten: Boolean = true,
    /** Endpoints this close (in grid units) to an existing point join it exactly. */
    val magnetRadius: Float = 0.6f,
)

object Geometry {

    private const val RESAMPLE_STEP = 0.2f
    /** Samples on each side used to measure a turn (0.6 grid units). */
    private const val CORNER_WINDOW = 3
    private const val CORNER_ANGLE_DEG = 55.0
    private const val STRAIGHT_TOLERANCE = 0.3f
    private const val CURVE_EPSILON = 0.15f
    private const val SAMPLES_PER_SEGMENT = 10

    /** Turns a raw finger stroke into a clean shape, or null if it's too small to keep. */
    fun recognize(raw: List<Pt>, existing: List<Shape>, options: DrawOptions): Shape? {
        if (raw.size < 2) return null
        val length = pathLength(raw)
        if (length < 0.3f) return null
        val closed = length > 3f && raw.first().dist(raw.last()) < max(0.8f, 0.12f * length)

        val (pts, corners) = if (options.straighten) {
            straighten(resample(raw, RESAMPLE_STEP), closed)
        } else {
            val simple = rdp(raw, 0.08f).let { if (closed && it.size > 3) it.dropLast(1) else it }
            simple to simple.indices.map { i -> !closed && (i == 0 || i == simple.lastIndex) }
        }
        if (pts.size < 2 || (closed && pts.size < 3)) return null

        val targets = cornerPoints(existing)
        val snapped = pts.mapIndexed { i, p ->
            when {
                !corners[i] -> p
                !closed && (i == 0 || i == pts.lastIndex) ->
                    magnet(p, targets, options.magnetRadius) ?: if (options.snapToGrid) snap(p) else p
                options.snapToGrid -> snap(p)
                else -> p
            }
        }
        // Snapping can collapse neighbouring points onto each other; drop the duplicates.
        val keep = snapped.indices.filter { i -> i == 0 || snapped[i] != snapped[i - 1] }
            .let { k -> if (closed && k.size > 1 && snapped[k.last()] == snapped[k.first()]) k.dropLast(1) else k }
        val finalPts = keep.map { snapped[it] }
        val finalCorners = keep.map { corners[it] }
        if (finalPts.size < 2 || (closed && finalPts.size < 3)) return null
        return Shape(finalPts, finalCorners, closed)
    }

    /**
     * Finds corners (sharp direction changes), then makes each stretch between corners either a
     * straight line or a smooth curve, depending on how far it strays from straight.
     */
    private fun straighten(path: List<Pt>, closed: Boolean): Pair<List<Pt>, List<Boolean>> {
        // A closed loop's last sample repeats the start; work on the loop without it.
        val loop = if (closed && path.size > 2) path.dropLast(1) else path
        val n = loop.size
        val cornerIdx = findCorners(loop, closed)

        val outPts = mutableListOf<Pt>()
        val outCorners = mutableListOf<Boolean>()

        if (closed && cornerIdx.isEmpty()) {
            // A round shape with no corners: all smooth points.
            val ring = rdp(loop + loop.first(), CURVE_EPSILON).dropLast(1)
            return ring to List(ring.size) { false }
        }

        val anchors = if (closed) cornerIdx + (cornerIdx.first() + n) else cornerIdx
        for (k in 0 until anchors.size - 1) {
            val from = anchors[k]
            val to = anchors[k + 1]
            val run = (from..to).map { loop[it % n] }
            outPts += run.first()
            outCorners += true
            val chord = run.first().dist(run.last())
            val deviation = run.maxOf { distToSegment(it, run.first(), run.last()) }
            if (deviation > max(STRAIGHT_TOLERANCE, 0.06f * chord)) {
                rdp(run, CURVE_EPSILON).drop(1).dropLast(1).forEach {
                    outPts += it
                    outCorners += false
                }
            }
        }
        if (!closed) {
            outPts += loop.last()
            outCorners += true
        }
        return outPts to outCorners
    }

    /** Indices of sharp turns, plus both ends of an open stroke. */
    private fun findCorners(pts: List<Pt>, closed: Boolean): List<Int> {
        val n = pts.size
        val w = CORNER_WINDOW
        if (n < 2 * w + 1) return if (closed) emptyList() else listOf(0, n - 1)
        val angle = DoubleArray(n)
        for (i in 0 until n) {
            if (!closed && (i < w || i > n - 1 - w)) continue
            angle[i] = turnAngleDeg(pts[(i - w + n) % n], pts[i], pts[(i + w) % n])
        }
        val corners = mutableListOf<Int>()
        if (!closed) corners += 0
        for (i in 0 until n) {
            if (angle[i] < CORNER_ANGLE_DEG) continue
            // Keep only the sharpest point of each bend.
            val isPeak = (1..w).all { d ->
                val before = if (closed) (i - d + n) % n else i - d
                val after = if (closed) (i + d) % n else i + d
                (before < 0 || angle[i] >= angle[before]) && (after >= n || angle[i] > angle[after])
            }
            if (isPeak) corners += i
        }
        if (!closed) {
            // Drop corners hugging the ends, then add the last point.
            corners.removeAll { it != 0 && it > n - 1 - w }
            corners += n - 1
        }
        return corners
    }

    /** Evenly spaced points along the stroke, so corner detection doesn't depend on finger speed. */
    fun resample(pts: List<Pt>, step: Float): List<Pt> {
        val out = mutableListOf(pts.first())
        var carry = 0f
        for ((a, b) in pts.zipWithNext()) {
            val seg = a.dist(b)
            if (seg == 0f) continue
            var t = step - carry
            while (t <= seg) {
                out += a + (b - a) * (t / seg)
                t += step
            }
            carry = seg - (t - step)
        }
        if (out.last() != pts.last()) out += pts.last()
        return out
    }

    /** A straight line from where the finger went down to where it lifted. */
    fun line(from: Pt, to: Pt, existing: List<Shape>, options: DrawOptions): Shape? {
        val targets = cornerPoints(existing)
        val a = pin(from, targets, options)
        val b = pin(to, targets, options)
        if (a == b) return null
        return Shape(listOf(a, b), listOf(true, true))
    }

    /** A rectangle with [from] and [to] as opposite corners. */
    fun box(from: Pt, to: Pt, existing: List<Shape>, options: DrawOptions): Shape? {
        val targets = cornerPoints(existing)
        val a = pin(from, targets, options)
        val b = pin(to, targets, options)
        if (a.x == b.x || a.y == b.y) return null
        return Shape(listOf(a, Pt(b.x, a.y), b, Pt(a.x, b.y)), List(4) { true }, closed = true)
    }

    /** An oval filling the box from [from] to [to]. */
    fun oval(from: Pt, to: Pt, options: DrawOptions): Shape? {
        val a = if (options.snapToGrid) snap(from) else from
        val b = if (options.snapToGrid) snap(to) else to
        if (a.x == b.x || a.y == b.y) return null
        val cx = (a.x + b.x) / 2
        val cy = (a.y + b.y) / 2
        val rx = abs(b.x - a.x) / 2
        val ry = abs(b.y - a.y) / 2
        val n = 16
        val pts = (0 until n).map { i ->
            val t = 2 * Math.PI * i / n
            Pt(cx + rx * kotlin.math.cos(t).toFloat(), cy + ry * kotlin.math.sin(t).toFloat())
        }
        return Shape(pts, List(n) { false }, closed = true)
    }

    private fun cornerPoints(existing: List<Shape>) = existing.filter { it.stroke && it.stamp == null }.flatMap { shape ->
        shape.points.filterIndexed { i, _ -> shape.corners.getOrElse(i) { false } }
    }

    private fun pin(p: Pt, targets: List<Pt>, options: DrawOptions): Pt =
        magnet(p, targets, options.magnetRadius) ?: if (options.snapToGrid) snap(p) else p

    fun snap(p: Pt) = Pt(p.x.roundToInt().toFloat(), p.y.roundToInt().toFloat())

    private fun magnet(p: Pt, targets: List<Pt>, radius: Float): Pt? =
        targets.minByOrNull { it.dist(p) }?.takeIf { it.dist(p) <= radius }

    /** How sharply the path turns at [b], in degrees (0 = straight on). */
    fun turnAngleDeg(a: Pt, b: Pt, c: Pt): Double {
        val u = b - a
        val v = c - b
        val lu = u.length()
        val lv = v.length()
        if (lu == 0f || lv == 0f) return 0.0
        val cos = ((u.x * v.x + u.y * v.y) / (lu * lv)).toDouble().coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    fun pathLength(pts: List<Pt>): Float = pts.zipWithNext { a, b -> a.dist(b) }.sum()

    /** Ramer-Douglas-Peucker simplification. */
    fun rdp(pts: List<Pt>, epsilon: Float): List<Pt> {
        if (pts.size < 3) return pts
        val keep = BooleanArray(pts.size)
        keep[0] = true
        keep[pts.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to pts.lastIndex)
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            var maxDist = 0f
            var index = -1
            for (i in start + 1 until end) {
                val d = distToSegment(pts[i], pts[start], pts[end])
                if (d > maxDist) {
                    maxDist = d
                    index = i
                }
            }
            if (index != -1 && maxDist > epsilon) {
                keep[index] = true
                stack.addLast(start to index)
                stack.addLast(index to end)
            }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    fun distToSegment(p: Pt, a: Pt, b: Pt): Float {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        if (len2 == 0f) return p.dist(a)
        val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
        return p.dist(a + ab * t)
    }

    /** The shape as a dense polyline, used for drawing, filling and hit testing. */
    fun outline(shape: Shape): List<Pt> {
        val p = shape.points
        if (p.size < 2) return p
        val out = ArrayList<Pt>(segmentCount(shape) * SAMPLES_PER_SEGMENT + 1)
        out += p[0]
        for (s in 0 until segmentCount(shape)) out += segmentSamples(shape, s)
        return out
    }

    private fun segmentCount(shape: Shape) =
        if (shape.points.size < 2) 0 else if (shape.closed) shape.points.size else shape.points.size - 1

    /** Points along segment [s] (from point s to the next), excluding its start. */
    private fun segmentSamples(shape: Shape, s: Int): List<Pt> {
        val p = shape.points
        val i1 = (s + 1) % p.size
        val a = p[s]
        val b = p[i1]
        val ta = tangent(shape, s)
        val tb = tangent(shape, i1)
        if (ta == null && tb == null) return listOf(b)
        val m0 = ta ?: Pt(0f, 0f)
        val m1 = tb ?: Pt(0f, 0f)
        return (1..SAMPLES_PER_SEGMENT).map { k -> hermite(a, b, m0, m1, k.toFloat() / SAMPLES_PER_SEGMENT) }
    }

    /** The segment of [shape] closest to [p], for inserting a new point there. */
    fun nearestSegment(shape: Shape, p: Pt): Int =
        (0 until segmentCount(shape)).minByOrNull { s ->
            val pts = listOf(shape.points[s]) + segmentSamples(shape, s)
            pts.zipWithNext { a, b -> distToSegment(p, a, b) }.min()
        } ?: 0

    /** Index of the point of [shape] nearest to [p] within [tolerance]. */
    fun pointAt(shape: Shape, p: Pt, tolerance: Float): Int? =
        shape.points.indices
            .minByOrNull { shape.points[it].dist(p) }
            ?.takeIf { shape.points[it].dist(p) <= tolerance }

    /** Catmull-Rom tangent at a smooth point; null at corners so their segments stay straight. */
    private fun tangent(shape: Shape, i: Int): Pt? {
        if (shape.corners.getOrElse(i) { true }) return null
        val p = shape.points
        val n = p.size
        val prev = if (shape.closed) p[(i - 1 + n) % n] else p[max(i - 1, 0)]
        val next = if (shape.closed) p[(i + 1) % n] else p[minOf(i + 1, n - 1)]
        return (next - prev) * 0.5f
    }

    private fun hermite(a: Pt, b: Pt, m0: Pt, m1: Pt, t: Float): Pt {
        val t2 = t * t
        val t3 = t2 * t
        val h00 = 2 * t3 - 3 * t2 + 1
        val h10 = t3 - 2 * t2 + t
        val h01 = -2 * t3 + 3 * t2
        val h11 = t3 - t2
        return a * h00 + m0 * h10 + b * h01 + m1 * h11
    }

    fun contains(shape: Shape, p: Pt): Boolean {
        if (!shape.closed) return false
        val poly = outline(shape)
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun area(shape: Shape): Float {
        val poly = outline(shape)
        var sum = 0f
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    fun distance(shape: Shape, p: Pt): Float {
        val poly = outline(shape).let { if (shape.closed) it + it.first() else it }
        if (poly.size == 1) return p.dist(poly[0])
        return poly.zipWithNext { a, b -> distToSegment(p, a, b) }.min()
    }

    /** Index of the smallest closed shape that contains [p], so filling picks the innermost piece. */
    fun fillTarget(shapes: List<Shape>, p: Pt): Int? =
        shapes.indices.filter { contains(shapes[it], p) }.minByOrNull { area(shapes[it]) }

    /** Index of the drawn line nearest to [p] within [tolerance] grid units (fill-only regions last). */
    fun hitTarget(shapes: List<Shape>, p: Pt, tolerance: Float): Int? {
        fun nearest(candidates: List<Int>) = candidates
            .map { it to distance(shapes[it], p) }
            .filter { it.second <= tolerance }
            .minByOrNull { it.second }
            ?.first
        return nearest(shapes.indices.filter { shapes[it].stroke })
            ?: nearest(shapes.indices.filter { !shapes[it].stroke })
    }

    private const val JOIN_TOLERANCE = 0.3f
    private const val MAX_LOOP_EDGES = 12
    private const val MAX_SEARCH_STEPS = 20_000

    /**
     * The smallest area around [p] enclosed by open lines that meet end to end, as a closed
     * outline, or null if those lines don't enclose [p].
     */
    fun enclosedRegion(shapes: List<Shape>, p: Pt): List<Pt>? {
        val edges = shapes.filter { it.stroke && !it.closed && it.points.size >= 2 }.map { outline(it) }
        if (edges.size < 2) return null

        // Group line ends that touch into shared nodes.
        val nodes = mutableListOf<Pt>()
        fun nodeOf(q: Pt): Int {
            val found = nodes.indexOfFirst { it.dist(q) <= JOIN_TOLERANCE }
            if (found >= 0) return found
            nodes += q
            return nodes.lastIndex
        }
        val ends = edges.map { nodeOf(it.first()) to nodeOf(it.last()) }
        val adjacency = HashMap<Int, MutableList<Int>>()
        ends.forEachIndexed { e, (a, b) ->
            if (a == b) return@forEachIndexed
            adjacency.getOrPut(a) { mutableListOf() } += e
            adjacency.getOrPut(b) { mutableListOf() } += e
        }

        var best: List<Pt>? = null
        var bestArea = Float.MAX_VALUE
        var steps = 0
        val used = BooleanArray(edges.size)

        fun walk(start: Int, at: Int, path: MutableList<Pt>, depth: Int) {
            if (++steps > MAX_SEARCH_STEPS || depth > MAX_LOOP_EDGES) return
            for (e in adjacency[at].orEmpty()) {
                if (used[e]) continue
                val (a, b) = ends[e]
                val forward = a == at
                val next = if (forward) b else a
                val pts = if (forward) edges[e] else edges[e].asReversed()
                used[e] = true
                val added = pts.drop(1)
                path += added
                if (next == start && depth >= 1) {
                    if (polygonContains(path, p)) {
                        val area = polygonArea(path)
                        if (area < bestArea) {
                            bestArea = area
                            best = path.toList()
                        }
                    }
                } else if (next != start) {
                    walk(start, next, path, depth + 1)
                }
                repeat(added.size) { path.removeAt(path.lastIndex) }
                used[e] = false
            }
        }

        for (start in adjacency.keys) {
            walk(start, start, mutableListOf(nodes[start]), 0)
        }
        return best?.let { if (it.first().dist(it.last()) <= JOIN_TOLERANCE) it.dropLast(1) else it }
    }

    private fun polygonContains(poly: List<Pt>, p: Pt): Boolean {
        var inside = false
        var j = poly.lastIndex
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    fun polygonArea(poly: List<Pt>): Float {
        var sum = 0f
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }
}
