package com.boro.notes

import com.boro.notes.sketch.Pt
import com.boro.notes.studio.Collage
import com.boro.notes.studio.CollageItem
import com.boro.notes.studio.CollageKind
import com.boro.notes.studio.Pad
import com.boro.notes.studio.PadShape
import com.boro.notes.studio.SketchPad
import com.boro.notes.studio.StudioJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sketch pad and collage model (plain Kotlin). */
class StudioTest {
    @Test
    fun mirrorFlipsAcrossTheMiddle() {
        val s = Pad.shape(PadShape.LINE, Pt(0.1f, 0.2f), Pt(0.3f, 0.4f), 0xFF000000, 4f)!!
        val m = Pad.mirror(s)
        listOf(0.9f, 0.2f, 0.7f, 0.4f).zip(m.points).forEach { (want, got) -> assertEquals(want, got, 0.0001f) }
    }

    @Test
    fun shapesCloseAndFillByTapInside() {
        val box = Pad.shape(PadShape.BOX, Pt(0.2f, 0.2f), Pt(0.6f, 0.6f), 0xFF000000, 4f)!!
        val line = Pad.shape(PadShape.LINE, Pt(0.1f, 0.1f), Pt(0.9f, 0.1f), 0xFF000000, 4f)!!
        assertTrue(box.closed)
        assertFalse(line.closed)
        val strokes = listOf(box, line)
        assertEquals(0, Pad.fillTarget(strokes, Pt(0.4f, 0.4f)))
        assertNull(Pad.fillTarget(strokes, Pt(0.8f, 0.8f)))
        assertNull(Pad.shape(PadShape.OVAL, Pt(0.5f, 0.5f), Pt(0.5f, 0.5f), 0, 4f))
    }

    @Test
    fun straightenedStrokeComesOutClean() {
        // A wobbly L: straight down then across.
        val raw = (0..40).map { Pt(0.2f + (if (it % 2 == 0) 0.002f else -0.002f), 0.2f + it * 0.01f) } +
            (1..40).map { Pt(0.2f + it * 0.01f, 0.6f + (if (it % 2 == 0) 0.002f else -0.002f)) }
        val s = Pad.stroke(raw, 0xFF000000, 4f, straighten = true)!!
        assertFalse(s.smooth)
        assertTrue("an L keeps few points, got ${s.pts().size}", s.pts().size <= 4)
        val loose = Pad.stroke(raw, 0xFF000000, 4f, straighten = false)!!
        assertTrue(loose.smooth)
    }

    @Test
    fun aTapLeavesADot() {
        val s = Pad.stroke(listOf(Pt(0.5f, 0.5f)), 0xFF000000, 4f, straighten = false)
        assertEquals(2, s!!.points.size)
    }

    @Test
    fun eraserFindsStrokesItCrosses() {
        val a = Pad.shape(PadShape.LINE, Pt(0.1f, 0.1f), Pt(0.9f, 0.1f), 0, 4f)!!
        val b = Pad.shape(PadShape.LINE, Pt(0.1f, 0.5f), Pt(0.9f, 0.5f), 0, 4f)!!
        assertEquals(setOf(1), Pad.touched(listOf(a, b), listOf(Pt(0.5f, 0.49f), Pt(0.5f, 0.51f)), 0.02f))
    }

    @Test
    fun contentRoundTrips() {
        val pad = SketchPad(strokes = listOf(Pad.shape(PadShape.BOX, Pt(0f, 0f), Pt(0.5f, 0.5f), 1, 2f)!!.copy(fill = 3)), photo = "a.jpg")
        assertEquals(pad, StudioJson.pad(StudioJson.encode(pad)))
        val collage = Collage(listOf(CollageItem("x", CollageKind.TEXT, text = "Hem", rot = 4f)), paper = 0xFF000000)
        assertEquals(collage, StudioJson.collage(StudioJson.encode(collage)))
        assertNotNull(StudioJson.pad("not json"))
    }

    @Test
    fun autoLevelSnapsStraightRunsToTheAngleStep() {
        // A slightly crooked L: both legs snap square at 90°.
        val pts = listOf(Pt(0f, 0f), Pt(1f, 0.08f), Pt(1.06f, 1f))
        val out = Pad.level(pts, listOf(true, true, true), 90)
        assertEquals(0f, out[1].y, 1e-4f)
        assertEquals(out[1].x, out[2].x, 1e-4f)
        // At 45° a near-diagonal becomes a true diagonal.
        val d = Pad.level(listOf(Pt(0f, 0f), Pt(1f, 0.9f)), listOf(true, true), 45)
        assertEquals(d[1].x, d[1].y, 1e-4f)
        // Off leaves it alone.
        assertEquals(pts, Pad.level(pts, listOf(true, true, true), 0))
    }

    @Test
    fun padGrowsAndTrims() {
        val pad = SketchPad(strokes = listOf(com.boro.notes.studio.PadStroke(0xFF000000, 3f, listOf(0.2f, 0.3f, 0.4f, 0.5f))))
        val left = Pad.grow(pad, com.boro.notes.studio.PadSide.LEFT, 0.5f)
        assertEquals(1.5f, left.padW, 1e-5f)
        // Growing left keeps the drawing where it was on the paper by shifting it right.
        assertEquals(0.7f, left.strokes[0].points[0], 1e-5f)
        val down = Pad.grow(pad, com.boro.notes.studio.PadSide.BOTTOM, 0.5f)
        assertEquals(pad.padH + 0.5f, down.padH, 1e-5f)
        assertEquals(0.3f, down.strokes[0].points[1], 1e-5f)
        val trimmed = Pad.trim(left)
        assertEquals(0.5f, trimmed.padW, 1e-5f)
        assertEquals(0.06f, trimmed.strokes[0].points[0], 1e-5f)
        assertEquals(0.06f, trimmed.strokes[0].points[1], 1e-5f)
    }
}
