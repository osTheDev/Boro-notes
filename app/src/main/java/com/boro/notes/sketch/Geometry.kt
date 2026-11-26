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
)

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

        val targets = existing.flatMap { shape ->
            shape.points.filterIndexed { i, _ -> shape.corners.getOrElse(i) { false } }
        }
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
        val n = p.size
        if (n < 2) return p
        val segments = if (shape.closed) n else n - 1
        val out = ArrayList<Pt>(segments * SAMPLES_PER_SEGMENT + 1)
        out += p[0]
        for (s in 0 until segments) {
            val i0 = s
            val i1 = (s + 1) % n
            val a = p[i0]
            val b = p[i1]
            val ta = tangent(shape, i0)
            val tb = tangent(shape, i1)
            if (ta == null && tb == null) {
                out += b
                continue
            }
            val m0 = ta ?: Pt(0f, 0f)
            val m1 = tb ?: Pt(0f, 0f)
            for (k in 1..SAMPLES_PER_SEGMENT) {
                val t = k.toFloat() / SAMPLES_PER_SEGMENT
                out += hermite(a, b, m0, m1, t)
            }
        }
        return out
    }

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

    /** Index of the shape nearest to [p] within [tolerance] grid units. */
    fun hitTarget(shapes: List<Shape>, p: Pt, tolerance: Float): Int? =
        shapes.indices
            .map { it to distance(shapes[it], p) }
            .filter { it.second <= tolerance }
            .minByOrNull { it.second }
            ?.first
}
