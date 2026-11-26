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

/** A note's content: a list of blocks, top to bottom. */
@Serializable
data class NoteDoc(val blocks: List<Block>) {

    /** Plain text of the whole note, used for search and the list preview. */
    fun plainText(htmlToText: (String) -> String = ::stripHtml): String = blocks.joinToString("\n") { b ->
        when (b) {
            is TextBlock -> b.text ?: htmlToText(b.html)
            is CheckBlock -> b.text
            is TableBlock -> b.rows.joinToString("\n") { it.joinToString("  ") }
            is ColumnsBlock -> b.columns.joinToString("\n", transform = htmlToText)
            is DividerBlock -> ""
        }
    }.trim()

    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

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
        return (if (left.isEmpty()) empty() else copy(blocks = left)).normalized()
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
data class TableBlock(override val id: String, val rows: List<List<String>>) : Block {
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
data class ColumnsBlock(override val id: String, val columns: List<String>) : Block

@Serializable
@SerialName("divider")
data class DividerBlock(override val id: String) : Block
