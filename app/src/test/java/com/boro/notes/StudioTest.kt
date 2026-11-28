package com.boro.notes

import com.boro.notes.sketch.Pt
import com.boro.notes.studio.Collage
import com.boro.notes.studio.CollageItem
import com.boro.notes.studio.CollageKind
import com.boro.notes.studio.Pad
import com.boro.notes.studio.PadShape
import com.boro.notes.studio.PadStroke
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
        val pad = Pad.editActive(SketchPad(photo = "a.jpg")) { listOf(Pad.shape(PadShape.BOX, Pt(0f, 0f), Pt(0.5f, 0.5f), 1, 2f)!!.copy(fill = 3)) }
        assertEquals(pad, StudioJson.pad(StudioJson.encode(pad)))
        // A sketch saved before layers opens with its strokes on Layer 1.
        val old = StudioJson.pad("""{"strokes":[{"color":1,"width":2.0,"points":[0.1,0.1,0.2,0.2]}],"photo":"b.jpg"}""")
        assertEquals(1, old.layers.size)
        assertEquals(1, old.layers[0].strokes.size)
        assertTrue(old.legacyStrokes.isEmpty())
        assertEquals("b.jpg", old.photo)
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
        val pad = Pad.editActive(SketchPad()) { listOf(com.boro.notes.studio.PadStroke(0xFF000000, 3f, listOf(0.2f, 0.3f, 0.4f, 0.5f))) }
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

    @Test
    fun smudgeEraserCutsOnlyWhatItTouches() {
        val line = PadStroke(1, 2f, listOf(0f, 0.5f, 1f, 0.5f), smooth = false)
        val out = Pad.eraseArea(listOf(line), listOf(Pt(0.5f, 0.5f)), 0.05f)
        assertEquals(2, out.size)
        assertTrue(out[0].points.chunked(2).all { it[0] < 0.46f })
        assertTrue(out[1].points.chunked(2).all { it[0] > 0.54f })
        // Missing it leaves it alone.
        assertEquals(listOf(line), Pad.eraseArea(listOf(line), listOf(Pt(0.5f, 0.9f)), 0.05f))
        // Cutting a filled box's outline keeps the fill.
        val box = Pad.shape(PadShape.BOX, Pt(0.2f, 0.2f), Pt(0.6f, 0.6f), 1, 2f)!!.copy(fill = 2)
        val cut = Pad.eraseArea(listOf(box), listOf(Pt(0.4f, 0.2f)), 0.03f)
        assertTrue(cut.any { it.fill == 2 && it.closed && it.width == 0f })
        assertTrue(cut.filter { it.width > 0f }.all { !it.closed && it.fill == null })
    }

    @Test
    fun fillFindsShapesMadeOfSeveralStrokesWithSmallGaps() {
        // A pocket drawn as three strokes, with a small gap at one corner.
        val sides = listOf(
            PadStroke(1, 3f, listOf(0.2f, 0.2f, 0.2f, 0.6f, 0.6f, 0.6f), smooth = false),
            PadStroke(1, 3f, listOf(0.6f, 0.6f, 0.6f, 0.2f), smooth = false),
            PadStroke(1, 3f, listOf(0.6f, 0.2f, 0.21f, 0.2f), smooth = false),
        )
        val poly = Pad.floodFill(sides, Pt(0.4f, 0.4f), 1f, 4f / 3f)
        assertNotNull(poly)
        val xs = poly!!.map { it.x }
        val ys = poly.map { it.y }
        assertTrue("fill reaches the lines", xs.min() < 0.22f && xs.max() > 0.58f && ys.min() < 0.22f && ys.max() > 0.58f)
        assertTrue("fill stays inside", xs.min() > 0.17f && xs.max() < 0.63f)
        assertTrue(Pad.contains(poly, Pt(0.4f, 0.4f)))
        // Outside any shape, or with a gap too big to jump, there's nothing to fill.
        assertNull(Pad.floodFill(sides, Pt(0.9f, 0.9f), 1f, 4f / 3f))
        val open = listOf(PadStroke(1, 3f, listOf(0.2f, 0.2f, 0.2f, 0.6f, 0.6f, 0.6f), smooth = false))
        assertNull(Pad.floodFill(open, Pt(0.4f, 0.4f), 1f, 4f / 3f))
        // The fill tool puts the area under the lines, and a second tap recolours it.
        val pad = Pad.editActive(SketchPad()) { sides }
        val filled = Pad.fill(pad, Pt(0.4f, 0.4f), 3)
        assertEquals(4, filled.strokes.size)
        assertEquals(3, filled.strokes.first().fill)
        assertEquals(0f, filled.strokes.first().width)
        assertEquals(5, Pad.fill(filled, Pt(0.4f, 0.4f), 5).strokes.first().fill)
        assertEquals(filled, Pad.fill(filled, Pt(0.9f, 0.9f), 3))
    }

    @Test
    fun layersAddMoveAndDelete() {
        var pad = SketchPad()
        pad = Pad.editActive(pad) { listOf(PadStroke(1, 2f, listOf(0.1f, 0.1f))) }
        pad = Pad.addLayer(pad)
        assertEquals(2, pad.layers.size)
        assertEquals(1, pad.active)
        assertEquals("Layer 2", pad.activeLayer.name)
        pad = Pad.editActive(pad) { it + PadStroke(2, 2f, listOf(0.2f, 0.2f)) }
        assertEquals(1, pad.layers[1].strokes.size)
        assertEquals(2, pad.strokes.size)
        pad = Pad.moveLayer(pad, 1, up = false)
        assertEquals("Layer 2", pad.layers[0].name)
        assertEquals(0, pad.active)
        pad = Pad.updateLayer(pad, 1) { it.copy(alpha = 0.25f, visible = false) }
        assertFalse(pad.layers[1].visible)
        pad = Pad.deleteLayer(pad, 0)
        assertEquals(listOf("Layer 1"), pad.layers.map { it.name })
        assertEquals(0, pad.active)
        assertEquals(pad, Pad.deleteLayer(pad, 0))
        // Drawing on a hidden layer shows it again.
        assertTrue(Pad.editActive(pad) { it }.layers[0].visible)
    }

    @Test
    fun boardSnapsTurnsTiesYarnAndLaysOutAMosaic() {
        assertEquals(90f, com.boro.notes.studio.Board.snapTurn(86f), 0f)
        assertEquals(-180f, com.boro.notes.studio.Board.snapTurn(-176.5f), 0f)
        assertEquals(30f, com.boro.notes.studio.Board.snapTurn(30f), 0f)

        val photos = (1..5).map { CollageItem("p$it", CollageKind.PHOTO, ref = "$it.jpg", x = it * 0.3f) }
        val note = CollageItem("n", CollageKind.TEXT, text = "Pocket idea")
        var c = Collage(photos + note)
        c = com.boro.notes.studio.Board.setMosaic(c, true)
        c = c.copy(mosaic = c.mosaic.copy(columns = 2, gap = 0.1f, w = 1.1f, x = 0f, y = 0f))
        val slots = com.boro.notes.studio.Board.mosaicSlots(c)
        assertEquals(5, slots.size)
        assertEquals(0.5f, slots.getValue("p1").w, 1e-5f)
        assertEquals(0.6f, slots.getValue("p2").x, 1e-5f)
        assertEquals(0.6f, slots.getValue("p3").y, 1e-5f)
        assertFalse("n" in slots)
        // Turning it off leaves the photos where the grid put them.
        val off = com.boro.notes.studio.Board.setMosaic(c, false)
        assertEquals(0.6f, off.items.first { it.id == "p2" }.x, 1e-5f)
        assertTrue(off.items.none { it.inMosaic })

        c = com.boro.notes.studio.Board.toggleLink(c, "p1", "n")
        assertEquals(1, c.links.size)
        assertEquals(c, com.boro.notes.studio.Board.toggleLink(c, "n", "n"))
        assertEquals(0, com.boro.notes.studio.Board.toggleLink(c, "n", "p1").links.size)
        val gone = com.boro.notes.studio.Board.remove(c, "n")
        assertTrue(gone.links.isEmpty())
        assertEquals(c, StudioJson.collage(StudioJson.encode(c)))
        // Old collages (no yarn, no mosaic) still open.
        assertEquals(1, StudioJson.collage("""{"items":[{"id":"a","kind":"TEXT"}]}""").items.size)
    }
}
