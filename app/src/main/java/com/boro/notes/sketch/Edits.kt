package com.boro.notes.sketch

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width get() = maxX - minX
    val height get() = maxY - minY
    val center get() = Pt((minX + maxX) / 2, (minY + maxY) / 2)
}

/** Editing operations on whole shapes: grouping, moving, mirroring, corners, topstitching. */
object Edits {

    fun bounds(shapes: List<Shape>): Bounds? {
        val pts = shapes.flatMap { s ->
            if (s.stamp != null) {
                val c = s.points.first()
                val r = s.stamp.radius
                listOf(Pt(c.x - r, c.y - r), Pt(c.x + r, c.y + r))
            } else {
                Geometry.outline(s)
            }
        }
        if (pts.isEmpty()) return null
        return Bounds(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    /** [index] plus every shape in the same group. */
    fun withGroup(shapes: List<Shape>, index: Int): Set<Int> {
        val group = shapes.getOrNull(index)?.group ?: return setOf(index)
        return shapes.indices.filter { shapes[it].group == group }.toSet()
    }

    /** A group id not used by any shape. */
    fun newGroupId(shapes: List<Shape>): Long = (shapes.mapNotNull { it.group }.maxOrNull() ?: 0L) + 1

    fun group(shapes: List<Shape>, selection: Set<Int>): List<Shape> {
        val id = newGroupId(shapes)
        return shapes.mapIndexed { i, s -> if (i in selection) s.copy(group = id) else s }
    }

    fun ungroup(shapes: List<Shape>, selection: Set<Int>): List<Shape> =
        shapes.mapIndexed { i, s -> if (i in selection) s.copy(group = null) else s }

    fun translate(shape: Shape, d: Pt): Shape = shape.copy(points = shape.points.map { it + d })

    fun move(shapes: List<Shape>, selection: Set<Int>, d: Pt): List<Shape> =
        shapes.mapIndexed { i, s -> if (i in selection) translate(s, d) else s }

    /** Grid-friendly move: whole squares when snapping. */
    fun snapDelta(d: Pt, snap: Boolean): Pt =
        if (snap) Pt(d.x.roundToInt().toFloat(), d.y.roundToInt().toFloat()) else d

    private fun flipShape(shape: Shape, axisX: Float): Shape {
        val pts = shape.points.map { Pt(2 * axisX - it.x, it.y) }
        return shape.copy(points = pts)
    }

    /** Flips the selection left-to-right in place. */
    fun flip(shapes: List<Shape>, selection: Set<Int>): List<Shape> {
        val b = bounds(selection.map { shapes[it] }) ?: return shapes
        val axis = (b.minX + b.maxX) / 2
        return shapes.mapIndexed { i, s -> if (i in selection) flipShape(s, axis) else s }
    }

    /**
     * Copies of the selection, each moved by [offset]; grouped copies get fresh group ids so they
     * stay grouped among themselves. With [mirror], copies are flipped (for the other leg or side).
     * Returns the new list and the indices of the copies.
     */
    fun duplicate(shapes: List<Shape>, selection: Set<Int>, mirror: Boolean, gap: Float = 1f): Pair<List<Shape>, Set<Int>> {
        val chosen = selection.sorted().map { shapes[it] }
        val b = bounds(chosen) ?: return shapes to emptySet()
        var nextGroup = newGroupId(shapes)
        val remap = HashMap<Long, Long>()
        val copies = chosen.map { s ->
            val moved = if (mirror) {
                // Reflect about the right edge, then leave a gap: a mirror image beside the original.
                flipShape(s, b.maxX + gap / 2)
            } else {
                translate(s, Pt(b.width + gap, 0f))
            }
            moved.copy(group = s.group?.let { g -> remap.getOrPut(g) { nextGroup++ } })
        }
        val result = shapes + copies
        return result to (shapes.size until result.size).toSet()
    }

    fun delete(shapes: List<Shape>, selection: Set<Int>): List<Shape> =
        shapes.filterIndexed { i, _ -> i !in selection }

    enum class CornerStyle { CLIPPED, ROUNDED }

    /** Clips or rounds every corner of the shape (not the ends of an open line). */
    fun corners(shape: Shape, style: CornerStyle, size: Float = 0.75f): Shape {
        if (shape.stamp != null || shape.points.size < 3) return shape
        val p = shape.points
        val n = p.size
        val outPts = mutableListOf<Pt>()
        val outCorners = mutableListOf<Boolean>()
        for (i in 0 until n) {
            val isEnd = !shape.closed && (i == 0 || i == n - 1)
            if (isEnd || !shape.corners.getOrElse(i) { true }) {
                outPts += p[i]
                outCorners += shape.corners.getOrElse(i) { true }
                continue
            }
            val prev = p[(i - 1 + n) % n]
            val next = p[(i + 1) % n]
            val inLen = prev.dist(p[i])
            val outLen = p[i].dist(next)
            val d = min(size, min(inLen, outLen) / 3f)
            if (d <= 0f) {
                outPts += p[i]
                outCorners += true
                continue
            }
            val a = p[i] + (prev - p[i]) * (d / inLen)
            val b = p[i] + (next - p[i]) * (d / outLen)
            when (style) {
                CornerStyle.CLIPPED -> {
                    outPts += listOf(a, b)
                    outCorners += listOf(true, true)
                }
                CornerStyle.ROUNDED -> {
                    // Quadratic curve from a to b with the old corner as control point.
                    for (k in 0..4) {
                        val t = k / 4f
                        val q = a * ((1 - t) * (1 - t)) + p[i] * (2 * (1 - t) * t) + b * (t * t)
                        outPts += q
                        outCorners += true
                    }
                }
            }
        }
        return shape.copy(points = outPts, corners = outCorners)
    }

    /**
     * A dashed stitch line running [inset] grid units inside a closed shape, like topstitching
     * around a pocket. Returns null for open lines and stamps.
     */
    fun topstitch(shape: Shape, inset: Float = 0.35f): Shape? {
        if (!shape.closed || shape.stamp != null) return null
        val poly = dedupe(Geometry.outline(shape))
        if (poly.size < 3) return null
        // Positive signed area = clockwise in screen coordinates (y down).
        val signed = poly.indices.sumOf { i ->
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            (a.x * b.y - b.x * a.y).toDouble()
        }
        val inward = if (signed > 0) 1f else -1f
        val n = poly.size
        val out = poly.indices.map { i ->
            val prev = poly[(i - 1 + n) % n]
            val cur = poly[i]
            val next = poly[(i + 1) % n]
            val n1 = normal(prev, cur)
            val n2 = normal(cur, next)
            val avg = Pt(n1.x + n2.x, n1.y + n2.y)
            val len = hypot(avg.x, avg.y)
            if (len < 1e-4f) return@map cur + n1 * (inset * inward)
            val dir = avg * (1f / len)
            // Keep the offset distance true along both edges (miter), within reason.
            val cos = max(0.35f, dir.x * n1.x + dir.y * n1.y)
            cur + dir * (inset * inward / cos)
        }
        return Shape(
            points = out,
            corners = List(out.size) { true },
            closed = true,
            style = LineStyle.STITCH,
            group = shape.group,
        )
    }

    private fun normal(a: Pt, b: Pt): Pt {
        val d = b - a
        val len = hypot(d.x, d.y)
        if (len == 0f) return Pt(0f, 0f)
        // Right-hand normal in screen coordinates.
        return Pt(-d.y / len, d.x / len)
    }

    private fun dedupe(pts: List<Pt>): List<Pt> {
        val out = mutableListOf<Pt>()
        for (p in pts) if (out.isEmpty() || out.last().dist(p) > 1e-3f) out += p
        if (out.size > 1 && out.first().dist(out.last()) <= 1e-3f) out.removeAt(out.lastIndex)
        return out
    }

    /** Pattern lines covering the shape's bounds, in grid units; the caller clips them to the shape. */
    fun patternLines(shape: Shape, pattern: FillPattern): List<Pair<Pt, Pt>> {
        val b = bounds(listOf(shape)) ?: return emptyList()
        val lines = mutableListOf<Pair<Pt, Pt>>()
        when (pattern) {
            FillPattern.LINES -> {
                var x = b.minX + 0.75f
                while (x < b.maxX) { lines += Pt(x, b.minY) to Pt(x, b.maxY); x += 0.75f }
            }
            FillPattern.CROSSHATCH -> {
                var x = b.minX + 1f
                while (x < b.maxX) { lines += Pt(x, b.minY) to Pt(x, b.maxY); x += 1f }
                var y = b.minY + 1f
                while (y < b.maxY) { lines += Pt(b.minX, y) to Pt(b.maxX, y); y += 1f }
            }
            FillPattern.DIAMOND -> {
                val span = b.width + b.height
                var c = -span
                val step = 1.4f
                while (c < span) {
                    // Lines of slope +1 and -1 through the bounds.
                    lines += Pt(b.minX + c, b.minY) to Pt(b.minX + c + span, b.minY + span)
                    lines += Pt(b.maxX - c, b.minY) to Pt(b.maxX - c - span, b.minY + span)
                    c += step
                }
            }
        }
        return lines
    }

    fun area(b: Bounds) = abs(b.width * b.height)
}
