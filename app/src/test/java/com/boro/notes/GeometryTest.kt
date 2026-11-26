package com.boro.notes

import com.boro.notes.sketch.DrawOptions
import com.boro.notes.sketch.Geometry
import com.boro.notes.sketch.Pt
import com.boro.notes.sketch.Shape
import com.boro.notes.sketch.SketchCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {
    private val defaults = DrawOptions()

    /** Points along a path with a little finger wobble. */
    private fun wobbly(vararg corners: Pt, perSegment: Int = 20): List<Pt> {
        val out = mutableListOf<Pt>()
        corners.toList().zipWithNext().forEachIndexed { s, (a, b) ->
            for (k in 0 until perSegment) {
                val t = k.toFloat() / perSegment
                val wobble = if (k % 2 == 0) 0.08f else -0.08f
                out += Pt(a.x + (b.x - a.x) * t + wobble * (s % 2), a.y + (b.y - a.y) * t + wobble * ((s + 1) % 2))
            }
        }
        out += corners.last()
        return out
    }

    @Test
    fun wobblyLineBecomesStraightSnappedLine() {
        val raw = wobbly(Pt(0.2f, 0.1f), Pt(8.3f, 0.2f))
        val shape = Geometry.recognize(raw, emptyList(), defaults)!!
        assertEquals(listOf(Pt(0f, 0f), Pt(8f, 0f)), shape.points)
        assertFalse(shape.closed)
    }

    @Test
    fun lShapeKeepsItsCorner() {
        val raw = wobbly(Pt(0.1f, 0.1f), Pt(0.2f, 6f), Pt(5.9f, 6.1f))
        val shape = Geometry.recognize(raw, emptyList(), defaults)!!
        assertEquals(listOf(Pt(0f, 0f), Pt(0f, 6f), Pt(6f, 6f)), shape.points)
        assertTrue(shape.corners.all { it })
    }

    @Test
    fun roughRectangleClosesIntoFourCorners() {
        val raw = wobbly(Pt(0f, 0f), Pt(6.1f, 0.2f), Pt(5.8f, 8.1f), Pt(0.2f, 7.9f), Pt(0.3f, 0.2f))
        val shape = Geometry.recognize(raw, emptyList(), defaults)!!
        assertTrue(shape.closed)
        assertEquals(setOf(Pt(0f, 0f), Pt(6f, 0f), Pt(6f, 8f), Pt(0f, 8f)), shape.points.toSet())
        assertEquals(4, shape.points.size)
    }

    @Test
    fun roundStrokeStaysSmooth() {
        val raw = (0..60).map { i ->
            val a = 2 * PI * i / 60
            Pt((5 + 4 * cos(a)).toFloat(), (5 + 4 * sin(a)).toFloat())
        }
        val shape = Geometry.recognize(raw, emptyList(), defaults)!!
        assertTrue(shape.closed)
        assertTrue("a circle should have smooth points", shape.corners.any { !it })
    }

    @Test
    fun sleeveKeepsStraightSidesAndCurvedCap() {
        // Up the left side, a rounded cap over the top, down the right side.
        val left = wobbly(Pt(0f, 10f), Pt(0f, 4f))
        val cap = (1 until 30).map { i ->
            val a = PI - PI * i / 30
            Pt((3 + 3 * cos(a)).toFloat(), (4 - 3 * sin(a)).toFloat())
        }
        val right = wobbly(Pt(6f, 4f), Pt(6f, 10f))
        val shape = Geometry.recognize(left + cap + right, emptyList(), defaults)!!
        assertEquals(Pt(0f, 10f), shape.points.first())
        assertEquals(Pt(6f, 10f), shape.points.last())
        assertTrue("cap should be smooth", shape.corners.any { !it })
        assertTrue("cap stays above the shoulder line", shape.points.filter { it.y < 3f }.isNotEmpty())
    }

    @Test
    fun endpointJoinsNearbyExistingPoint() {
        val existing = listOf(Shape(listOf(Pt(0f, 0f), Pt(4f, 0f)), listOf(true, true)))
        val noSnap = defaults.copy(snapToGrid = false)
        val raw = wobbly(Pt(4.3f, 0.3f), Pt(4.2f, 5.4f))
        val shape = Geometry.recognize(raw, existing, noSnap)!!
        assertEquals(Pt(4f, 0f), shape.points.first())
        assertEquals(Pt(4.2f, 5.4f), shape.points.last())
    }

    @Test
    fun boxToolMakesPerfectSnappedRectangle() {
        val shape = Geometry.box(Pt(1.2f, 0.8f), Pt(6.7f, 4.4f), emptyList(), defaults)!!
        assertEquals(listOf(Pt(1f, 1f), Pt(7f, 1f), Pt(7f, 4f), Pt(1f, 4f)), shape.points)
        assertTrue(shape.closed)
        assertTrue(shape.corners.all { it })
        assertEquals(18f, Geometry.area(shape), 0.01f)
    }

    @Test
    fun lineToolSnapsAndJoinsExistingCorners() {
        val existing = listOf(Geometry.box(Pt(0f, 0f), Pt(4f, 4f), emptyList(), defaults)!!)
        val shape = Geometry.line(Pt(4.4f, 3.7f), Pt(9.2f, 3.9f), existing, defaults)!!
        assertEquals(listOf(Pt(4f, 4f), Pt(9f, 4f)), shape.points)
    }

    @Test
    fun ovalFitsItsBoxAndCanBeFilled() {
        val shape = Geometry.oval(Pt(0f, 0f), Pt(8f, 4f), defaults)!!
        assertTrue(shape.closed)
        assertEquals(0f, shape.points.minOf { it.x }, 0.01f)
        assertEquals(8f, shape.points.maxOf { it.x }, 0.01f)
        assertEquals(0, Geometry.fillTarget(listOf(shape), Pt(4f, 2f)))
        assertNull(Geometry.fillTarget(listOf(shape), Pt(0.3f, 0.3f)))
    }

    @Test
    fun flatBoxIsIgnored() {
        assertNull(Geometry.box(Pt(1f, 1f), Pt(5f, 1.2f), emptyList(), defaults))
    }

    @Test
    fun tinyStrokeIsIgnored() {
        assertNull(Geometry.recognize(listOf(Pt(1f, 1f), Pt(1.1f, 1.05f)), emptyList(), defaults))
    }

    @Test
    fun fillPicksInnermostClosedShape() {
        val outer = Shape(listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f), Pt(0f, 10f)), List(4) { true }, closed = true)
        val inner = Shape(listOf(Pt(2f, 2f), Pt(5f, 2f), Pt(5f, 5f), Pt(2f, 5f)), List(4) { true }, closed = true)
        val shapes = listOf(outer, inner)
        assertEquals(1, Geometry.fillTarget(shapes, Pt(3f, 3f)))
        assertEquals(0, Geometry.fillTarget(shapes, Pt(8f, 8f)))
        assertNull(Geometry.fillTarget(shapes, Pt(12f, 3f)))
    }

    @Test
    fun eraseHitsNearestShapeWithinTolerance() {
        val a = Shape(listOf(Pt(0f, 0f), Pt(10f, 0f)), listOf(true, true))
        val b = Shape(listOf(Pt(0f, 3f), Pt(10f, 3f)), listOf(true, true))
        assertEquals(1, Geometry.hitTarget(listOf(a, b), Pt(5f, 2.6f), 0.8f))
        assertNull(Geometry.hitTarget(listOf(a, b), Pt(5f, 1.5f), 0.8f))
    }

    @Test
    fun shapesSurviveSaveAndLoad() {
        val shapes = listOf(
            Shape(listOf(Pt(0f, 0f), Pt(2.5f, 1f), Pt(4f, 0f)), listOf(true, false, true), closed = false),
            Shape(listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f)), List(3) { true }, closed = true, fill = 0xFF64B5F6.toInt()),
        )
        assertEquals(shapes, SketchCodec.decode(SketchCodec.encode(shapes)))
        assertNotNull(SketchCodec.decode("not json"))
    }
}
