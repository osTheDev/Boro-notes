package com.boro.notes

import com.boro.notes.notes.CheckBlock
import com.boro.notes.notes.ColumnsBlock
import com.boro.notes.notes.DividerBlock
import com.boro.notes.notes.NoteDoc
import com.boro.notes.notes.PaperPattern
import com.boro.notes.notes.PaperColor
import com.boro.notes.notes.Paper
import com.boro.notes.notes.TableBlock
import com.boro.notes.notes.TextBlock
import com.boro.notes.notes.TextKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDocTest {
    @Test
    fun oldPlainNotesLoadAsText() {
        val doc = NoteDoc.load(null, "Raw selvedge\n14oz")
        val block = doc.blocks.single() as TextBlock
        assertEquals("Raw selvedge\n14oz", block.text)
        assertEquals("Raw selvedge\n14oz", doc.plainText())
        assertEquals(doc.blocks.size, NoteDoc.load("not json", "x").blocks.size)
    }

    @Test
    fun everyBlockTypeSurvivesSaving() {
        val doc = NoteDoc(
            listOf(
                TextBlock("a", "<p><b>Bold</b> plan</p>", TextKind.HEADING),
                CheckBlock("b", "Buy rivets", checked = true),
                TableBlock("c", listOf(listOf("Fabric", "Yards"), listOf("Denim", "3"))),
                ColumnsBlock("d", listOf("<p>Left</p>", "<p>Right</p>")),
                DividerBlock("e"),
                TextBlock("f"),
            )
        )
        assertEquals(doc, NoteDoc.load(doc.encode(), ""))
        val plain = doc.plainText()
        assertTrue(plain.contains("Bold plan"))
        assertTrue(plain.contains("Buy rivets"))
        assertTrue(plain.contains("Denim  3"))
        assertTrue(plain.contains("Right"))
    }

    @Test
    fun insertingKeepsATextBlockAtTheEnd() {
        val doc = NoteDoc(listOf(TextBlock("a"))).insertAfter("a", TableBlock.blank())
        assertEquals(3, doc.blocks.size)
        assertTrue(doc.blocks[1] is TableBlock)
        assertTrue(doc.blocks.last() is TextBlock)
        val removed = doc.remove(doc.blocks.last().id).remove("a")
        assertTrue(removed.blocks.last() is TextBlock)
    }

    @Test
    fun tableRowsAndColumns() {
        var t = TableBlock.blank(2, 2).setCell(0, 0, "A")
        t = t.addRow(0).addColumn(0)
        assertEquals(3, t.rowCount)
        assertEquals(3, t.columnCount)
        assertEquals(listOf("A", "", ""), t.rows[0])
        t = t.removeRow(1).removeColumn(1)
        assertEquals(2, t.rowCount)
        assertEquals(2, t.columnCount)
        // Never removes the last row or column.
        assertEquals(1, TableBlock.blank(1, 1).removeRow(0).rowCount)
    }

    @Test
    fun movingBlocks() {
        val doc = NoteDoc(listOf(TextBlock("a"), DividerBlock("b"), TextBlock("c")))
        assertEquals(listOf("b", "a", "c"), doc.move("b", -1).blocks.map { it.id })
        assertEquals(doc, doc.move("a", -1))
    }

    @Test
    fun stripsHtmlForSearch() {
        assertEquals("Hi & bye\nnext", NoteDoc.stripHtml("<p>Hi &amp; <b>bye</b></p><p>next</p>"))
    }

    @Test
    fun paperRoundTripsAndOldContentGetsPlainPaper() {
        val doc = NoteDoc.empty().copy(paper = Paper(PaperColor.KRAFT, PaperPattern.RULED, margin = true))
        assertEquals(doc, NoteDoc.load(doc.encode(), ""))
        val old = """{"blocks":[{"type":"text","id":"a","html":"<p>hi</p>"}]}"""
        assertEquals(Paper(), NoteDoc.load(old, "").paper)
        // Removing the last block keeps the paper.
        assertEquals(doc.paper, doc.remove(doc.blocks.single().id).paper)
    }
}
