package com.boro.notes

import com.boro.notes.sketch.DrawOptions
import com.boro.notes.sketch.Edits
import com.boro.notes.sketch.FillPattern
import com.boro.notes.sketch.Geometry
import com.boro.notes.sketch.LineStyle
import com.boro.notes.sketch.Preset
import com.boro.notes.sketch.Pt
import com.boro.notes.sketch.Shape
import com.boro.notes.sketch.SketchCodec
import com.boro.notes.sketch.Stamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {
    private val box = Geometry.box(Pt(0f, 0f), Pt(4f, 6f), emptyList(), DrawOptions())!!
    private val rivet = Shape(listOf(Pt(1f, 1f)), listOf(true), stamp = Stamp.RIVET)

    @Test
    fun groupingSelectsTogether() {
        val shapes = Edits.group(listOf(box, rivet, box), setOf(0, 1))
        assertEquals(setOf(0, 1), Edits.withGroup(shapes, 1))
        assertEquals(setOf(2), Edits.withGroup(shapes, 2))
        assertEquals(setOf(0), Edits.withGroup(Edits.ungroup(shapes, setOf(0, 1)), 0))
    }

    @Test
    fun moveSnapsToWholeSquares() {
        val d = Edits.snapDelta(Pt(2.4f, -0.6f), snap = true)
        assertEquals(Pt(2f, -1f), d)
        val moved = Edits.move(listOf(box, rivet), setOf(1), d)
        assertEquals(Pt(3f, 0f), moved[1].points[0])
        assertEquals(box, moved[0])
    }

    @Test
    fun mirrorCopyLandsBesideAndFlipped() {
        val slanted = Shape(listOf(Pt(0f, 0f), Pt(2f, 4f)), listOf(true, true), group = 7)
        val (shapes, copies) = Edits.duplicate(listOf(slanted), setOf(0), mirror = true)
        assertEquals(setOf(1), copies)
        // Original spans x 0..2; the mirror starts a 1-square gap to the right and slants the other way.
        assertEquals(listOf(Pt(5f, 0f), Pt(3f, 4f)), shapes[1].points)
        assertTrue(shapes[1].group != 7L && shapes[1].group != null)
    }

    @Test
    fun duplicatePlacesCopyToTheRight() {
        val (shapes, _) = Edits.duplicate(listOf(box), setOf(0), mirror = false)
        assertEquals(Pt(5f, 0f), shapes[1].points[0])
    }

    @Test
    fun clippedAndRoundedCorners() {
        val clipped = Edits.corners(box, Edits.CornerStyle.CLIPPED, 1f)
        assertEquals(8, clipped.points.size)
        assertEquals(Pt(0f, 1f), clipped.points[0])
        assertEquals(Pt(1f, 0f), clipped.points[1])
        assertTrue(Geometry.area(clipped) < Geometry.area(box))
        val rounded = Edits.corners(box, Edits.CornerStyle.ROUNDED, 1f)
        assertEquals(20, rounded.points.size)
    }

    @Test
    fun topstitchRunsInsideTheEdge() {
        val stitch = Edits.topstitch(box, 0.5f)!!
        assertEquals(LineStyle.STITCH, stitch.style)
        val b = Edits.bounds(listOf(stitch))!!
        assertEquals(0.5f, b.minX, 0.01f)
        assertEquals(3.5f, b.maxX, 0.01f)
        assertEquals(5.5f, b.maxY, 0.01f)
        assertNull(Edits.topstitch(Shape(listOf(Pt(0f, 0f), Pt(3f, 0f)), listOf(true, true))))
        // Works whichever way round the shape was drawn.
        val reversed = box.copy(points = box.points.reversed())
        assertEquals(0.5f, Edits.bounds(listOf(Edits.topstitch(reversed, 0.5f)!!))!!.minX, 0.01f)
    }

    @Test
    fun patternsCoverTheShape() {
        FillPattern.entries.forEach { assertTrue(Edits.patternLines(box, it).isNotEmpty()) }
        assertEquals(5, Edits.patternLines(box, FillPattern.LINES).size)
    }

    @Test
    fun presetsAreValidAndSurviveSaving() {
        Preset.entries.forEach { preset ->
            val shapes = preset.shapes()
            assertEquals(shapes, SketchCodec.decode(SketchCodec.encode(shapes)))
            shapes.forEach { assertEquals(it.points.size, it.corners.size) }
        }
        val jeans = Preset.JEANS_BACK.shapes()
        assertTrue(jeans.size > 10)
        assertNotNull(jeans.firstOrNull { it.style == LineStyle.STITCH && it.closed })
    }
}
