package com.boro.notes.notes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** How a text block is styled as a whole. */
@Serializable
enum class TextKind(val label: String) {
    BODY("Body"),
    TITLE("Title"),
    HEADING("Heading"),
    SUBHEADING("Subheading"),
    QUOTE("Quote"),
}

/** The page a note is written on. Every note can look different. */
@Serializable
data class Paper(
    val color: PaperColor = PaperColor.DEFAULT,
    val pattern: PaperPattern = PaperPattern.PLAIN,
    /** A notebook margin line down the left. */
    val margin: Boolean = false,
)

/** Page colours. [argb] null follows the app theme; [dark] pages get light text. */
@Serializable
enum class PaperColor(val label: String, val argb: Long?, val dark: Boolean = false) {
    DEFAULT("Default", null),
    CREAM("Cream", 0xFFFBF5E6),
    KRAFT("Kraft", 0xFFE6D3AE),
    SAGE("Sage", 0xFFE3EADB),
    BLUSH("Blush", 0xFFF6E3DF),
    SKY("Sky", 0xFFE2ECF6),
    DENIM("Denim", 0xFF22385A, dark = true),
    INDIGO("Indigo", 0xFF1A1D33, dark = true),
    CHARCOAL("Charcoal", 0xFF262626, dark = true),
}

/** Solid fill for tables and columns, hiding the paper's lines. [argb] null uses the paper's own colour. */
@Serializable
enum class BoxFill(val label: String, val argb: Long?) {
    PAPER("Paper", null),
    WHITE("White", 0xFFFFFFFF),
    CREAM("Cream", 0xFFFFF6DD),
    BUTTER("Butter", 0xFFFFEDA8),
    KRAFT("Kraft", 0xFFE9D7B5),
    SAGE("Sage", 0xFFDDE8D3),
    SKY("Sky", 0xFFD8E7F5),
    BLUSH("Blush", 0xFFF7DCD7),
    LILAC("Lilac", 0xFFE6DDF3),
}

@Serializable
enum class PaperPattern(val label: String) {
    PLAIN("Plain"),
    RULED("Ruled lines"),
    GRID("Grid"),
    GRAPH("Graph (fine grid)"),
    DOTS("Dots"),
}

/** A note's content: a list of blocks, top to bottom, on a [paper]. */
@Serializable
data class NoteDoc(val blocks: List<Block>, val paper: Paper = Paper()) {

    /** Plain text of the whole note, used for search and the list preview. */
    fun plainText(htmlToText: (String) -> String = ::stripHtml): String = blocks.joinToString("\n") { b ->
        when (b) {
            is TextBlock -> b.text ?: htmlToText(b.html)
            is CheckBlock -> b.text
            is TableBlock -> b.rows.joinToString("\n") { it.joinToString("  ") }
            is ColumnsBlock -> b.columns.joinToString("\n", transform = htmlToText)
            is DividerBlock -> ""
            is ImageBlock -> b.caption
            is DrawingBlock -> b.caption
        }
    }.trim()

    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

        /** A paper setting on its own (e.g. a journal's page paper), as JSON. */
        fun encodePaper(paper: Paper): String = json.encodeToString(paper)
        fun decodePaper(text: String?): Paper? = text?.let { runCatching { json.decodeFromString<Paper>(it) }.getOrNull() }

        fun newId(): String = UUID.randomUUID().toString().take(8)

        fun empty() = NoteDoc(listOf(TextBlock(newId())))

        /** A note saved before formatting existed: its plain text becomes one text block. */
        fun fromPlain(text: String) = NoteDoc(listOf(TextBlock(newId(), text = text)))

        /** The note's document, from saved [content] or else from its plain [body]. */
        fun load(content: String?, body: String): NoteDoc =
            content?.let { runCatching { json.decodeFromString<NoteDoc>(it) }.getOrNull() }
                ?.takeIf { it.blocks.isNotEmpty() }
                ?.normalized()
                ?: fromPlain(body)

        /** Rough HTML-to-text, good enough for search when the editor isn't available. */
        fun stripHtml(html: String): String = html
            .replace(Regex("(?i)<br\\s*/?>|</p>|</li>|</h[1-6]>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
            .lines().joinToString("\n") { it.trimEnd() }
            .trim()
    }

    /** Always ends with a text block, so there's somewhere to keep typing after a table or divider. */
    fun normalized(): NoteDoc =
        if (blocks.lastOrNull() is TextBlock) this else copy(blocks = blocks + TextBlock(newId()))

    fun indexOf(id: String) = blocks.indexOfFirst { it.id == id }

    /** Inserts [block] after the block [afterId] (or at the end), keeping the doc normalized. */
    fun insertAfter(afterId: String?, block: Block): NoteDoc {
        val at = afterId?.let(::indexOf)?.takeIf { it >= 0 }?.plus(1) ?: blocks.size
        return copy(blocks = blocks.toMutableList().also { it.add(at, block) }).normalized()
    }

    fun replace(block: Block): NoteDoc = copy(blocks = blocks.map { if (it.id == block.id) block else it })

    fun remove(id: String): NoteDoc {
        val left = blocks.filterNot { it.id == id }
        return copy(blocks = left.ifEmpty { listOf(TextBlock(newId())) }).normalized()
    }

    fun move(id: String, by: Int): NoteDoc {
        val i = indexOf(id)
        val j = i + by
        if (i < 0 || j !in blocks.indices) return this
        val list = blocks.toMutableList()
        val b = list.removeAt(i)
        list.add(j, b)
        return copy(blocks = list).normalized()
    }
}

@Serializable
sealed interface Block {
    val id: String
}

/** Rich text. [html] is the formatted text; [text] holds plain text from before formatting existed. */
@Serializable
@SerialName("text")
data class TextBlock(
    override val id: String,
    val html: String = "",
    val kind: TextKind = TextKind.BODY,
    val text: String? = null,
) : Block

@Serializable
@SerialName("check")
data class CheckBlock(override val id: String, val text: String = "", val checked: Boolean = false) : Block

@Serializable
@SerialName("table")
data class TableBlock(override val id: String, val rows: List<List<String>>, val fill: BoxFill? = null) : Block {
    val rowCount get() = rows.size
    val columnCount get() = rows.maxOfOrNull { it.size } ?: 0

    fun setCell(r: Int, c: Int, value: String) =
        copy(rows = rows.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, v -> if (j == c) value else v } else row })

    fun addRow(after: Int = rowCount - 1) =
        copy(rows = rows.toMutableList().also { it.add((after + 1).coerceIn(0, rowCount), List(columnCount) { "" }) })

    fun addColumn(after: Int = columnCount - 1) =
        copy(rows = rows.map { row -> row.toMutableList().also { it.add((after + 1).coerceIn(0, row.size), "") } })

    fun removeRow(r: Int) = if (rowCount <= 1) this else copy(rows = rows.filterIndexed { i, _ -> i != r })

    fun removeColumn(c: Int) = if (columnCount <= 1) this else copy(rows = rows.map { row -> row.filterIndexed { j, _ -> j != c } })

    companion object {
        fun blank(rows: Int = 3, columns: Int = 3) = TableBlock(NoteDoc.newId(), List(rows) { List(columns) { "" } })
    }
}

/** Side-by-side rich text columns (stacked on the cover screen). */
@Serializable
@SerialName("columns")
data class ColumnsBlock(override val id: String, val columns: List<String>, val fill: BoxFill? = null) : Block

@Serializable
@SerialName("divider")
data class DividerBlock(override val id: String) : Block

/** A photo stored in the app's files ([file] is its name in the images folder). [width] is the share of the page width. */
@Serializable
@SerialName("image")
data class ImageBlock(override val id: String, val file: String, val caption: String = "", val width: Float = 1f) : Block

/** A finger drawing. [height] is in dp; stroke x positions are fractions of the width so it scales across screens. */
@Serializable
@SerialName("drawing")
data class DrawingBlock(
    override val id: String,
    val strokes: List<InkStroke> = emptyList(),
    val height: Int = 280,
    val caption: String = "",
) : Block

/** One stroke: colour (ARGB), width in dp, and points as x (fraction of width), y (dp) pairs. */
@Serializable
data class InkStroke(val color: Long, val width: Float, val points: List<Float>)
