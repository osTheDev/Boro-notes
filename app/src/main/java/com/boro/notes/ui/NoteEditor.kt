package com.boro.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.boro.notes.notes.Block
import com.boro.notes.notes.CheckBlock
import com.boro.notes.notes.ColumnsBlock
import com.boro.notes.notes.DividerBlock
import com.boro.notes.notes.NoteDoc
import com.boro.notes.notes.TableBlock
import com.boro.notes.notes.TextBlock
import com.boro.notes.notes.TextKind
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

const val TAG_NOTE_EDITOR = "noteEditor"
const val TAG_FORMAT_BAR = "formatBar"
const val TAG_NOTE_TABLE = "noteTable"
const val TAG_NOTE_COLUMNS = "noteColumns"
const val TAG_NOTE_CHECK = "noteCheck"

private val TEXT_COLORS = listOf(
    "Red" to Color(0xFFE53935),
    "Orange" to Color(0xFFFB8C00),
    "Green" to Color(0xFF43A047),
    "Blue" to Color(0xFF1E88E5),
    "Purple" to Color(0xFF8E24AA),
)
private val HIGHLIGHTS = listOf(
    "Yellow" to Color(0x66FFEB3B),
    "Green" to Color(0x6666BB6A),
    "Blue" to Color(0x6642A5F5),
    "Pink" to Color(0x66F06292),
)

/**
 * The formatted note body: rich text, checklists, tables, columns and dividers, with a compact
 * formatting bar. Reports every change (debounced) as document JSON plus plain text.
 */
@Composable
fun NoteEditor(note: EditorState, onChange: (content: String, plain: String) -> Unit, wide: Boolean, modifier: Modifier = Modifier) {
    // The editor owns the document once a note is open; reload only when another note opens.
    key(note.id) {
        NoteEditorContent(note, onChange, wide, modifier)
    }
}

/** Editor state for one open note: the document plus a rich text state per text area. */
private class DocEditor(initial: NoteDoc) {
    var doc by mutableStateOf(initial)
        private set
    /** Rich text areas keyed by block id, or "blockId#column" for columns. */
    val rich = HashMap<String, RichTextState>()
    /** What has focus: a block id, "id#column" or "id@row,col" for a table cell. */
    var focused by mutableStateOf<String?>(null)
    /** A block to focus as soon as it's on screen (e.g. a new checklist item). */
    var pendingFocus by mutableStateOf<String?>(null)

    init {
        ensureStates(initial)
    }

    private fun ensureStates(d: NoteDoc) {
        for (b in d.blocks) when (b) {
            is TextBlock -> rich.getOrPut(b.id) {
                RichTextState().apply { if (b.text != null) setText(b.text) else if (b.html.isNotEmpty()) setHtml(b.html) }
            }
            is ColumnsBlock -> b.columns.forEachIndexed { i, html ->
                rich.getOrPut("${b.id}#$i") { RichTextState().apply { if (html.isNotEmpty()) setHtml(html) } }
            }
            else -> Unit
        }
    }

    fun update(d: NoteDoc) {
        ensureStates(d)
        doc = d
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Block> latest(b: T): T = (doc.blocks.firstOrNull { it.id == b.id } as? T) ?: b

    val focusedBlockId: String? get() = focused?.substringBefore('#')?.substringBefore('@')

    val activeRich: RichTextState? get() = focused?.let { rich[it] }

    /** The current document with all rich text written out as HTML. */
    fun snapshot(): NoteDoc = doc.copy(blocks = doc.blocks.map { b ->
        when (b) {
            is TextBlock -> rich[b.id]?.let { b.copy(html = it.toHtml(), text = null) } ?: b
            is ColumnsBlock -> b.copy(columns = b.columns.mapIndexed { i, h -> rich["${b.id}#$i"]?.toHtml() ?: h })
            else -> b
        }
    })

    fun plain(): String = doc.blocks.joinToString("\n") { b ->
        when (b) {
            is TextBlock -> rich[b.id]?.annotatedString?.text ?: ""
            is CheckBlock -> b.text
            is TableBlock -> b.rows.joinToString("\n") { it.joinToString("  ") }
            is ColumnsBlock -> b.columns.indices.joinToString("\n") { rich["${b.id}#$it"]?.annotatedString?.text ?: "" }
            is DividerBlock -> ""
        }
    }.trim()

    /**
     * Adds a block after the focused one. From the formatting bar the new block is only selected
     * (so its menus show) and the keyboard stays shut; typing into it is one tap away.
     * [keyboard] moves real focus there, for flows that are already typing (checklist Enter).
     */
    fun insert(block: Block, focusKey: String = block.id, keyboard: Boolean = false) {
        update(doc.insertAfter(focusedBlockId, block))
        if (keyboard) pendingFocus = focusKey else focused = focusKey
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun NoteEditorContent(note: EditorState, onChange: (String, String) -> Unit, wide: Boolean, modifier: Modifier) {
    val editor = remember { DocEditor(NoteDoc.load(note.content, note.body)) }
    val latestOnChange by rememberUpdatedState(onChange)

    // Save shortly after typing stops.
    LaunchedEffect(editor) {
        snapshotFlow {
            val d = editor.doc
            d to d.blocks.flatMap { b ->
                when (b) {
                    is TextBlock -> listOfNotNull(editor.rich[b.id]?.annotatedString)
                    is ColumnsBlock -> b.columns.indices.mapNotNull { editor.rich["${b.id}#$it"]?.annotatedString }
                    else -> emptyList()
                }
            }
        }
            .drop(1)
            .debounce(300)
            .collect { latestOnChange(editor.snapshot().encode(), editor.plain()) }
    }

    Column(modifier) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(TAG_NOTE_EDITOR),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            editor.doc.blocks.forEachIndexed { index, block ->
                key(block.id) {
                    when (block) {
                        is TextBlock -> TextBlockView(editor, block, placeholder = index == 0 && editor.doc.blocks.size == 1)
                        is CheckBlock -> CheckBlockView(editor, block)
                        is TableBlock -> TableBlockView(editor, block, wide)
                        is ColumnsBlock -> ColumnsBlockView(editor, block, wide)
                        is DividerBlock -> DividerBlockView(editor, block)
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
        FormattingBar(editor)
    }
}

@Composable
private fun Modifier.trackFocus(editor: DocEditor, key: String): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(editor.pendingFocus) {
        if (editor.pendingFocus == key) {
            runCatching { requester.requestFocus() }
            editor.pendingFocus = null
        }
    }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) editor.focused = key }
}

@Composable
private fun richTextStyle(kind: TextKind): TextStyle {
    val t = MaterialTheme.typography
    val base = when (kind) {
        TextKind.TITLE -> t.headlineMedium.copy(fontWeight = FontWeight.Bold)
        TextKind.HEADING -> t.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        TextKind.SUBHEADING -> t.titleMedium.copy(fontWeight = FontWeight.Medium)
        TextKind.QUOTE -> t.bodyLarge.copy(fontStyle = FontStyle.Italic)
        TextKind.BODY -> t.bodyLarge
    }
    return base.copy(color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun RichArea(editor: DocEditor, key: String, style: TextStyle, placeholder: String?, modifier: Modifier = Modifier) {
    val state = editor.rich[key] ?: return
    BasicRichTextEditor(
        state = state,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth().trackFocus(editor, key),
        decorationBox = { inner ->
            Box {
                if (placeholder != null && state.annotatedString.text.isEmpty()) {
                    Text(placeholder, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                inner()
            }
        },
    )
}

@Composable
private fun TextBlockView(editor: DocEditor, block: TextBlock, placeholder: Boolean) {
    val style = richTextStyle(block.kind)
    val hint = if (placeholder) "Start writing" else null
    if (block.kind == TextKind.QUOTE) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
            RichArea(editor, block.id, style, hint, Modifier.padding(start = 12.dp))
        }
    } else {
        RichArea(editor, block.id, style, hint)
    }
}

@Composable
private fun CheckBlockView(editor: DocEditor, block: CheckBlock) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(TAG_NOTE_CHECK)) {
        Checkbox(
            checked = block.checked,
            onCheckedChange = { editor.update(editor.doc.replace(editor.latest(block).copy(checked = it))) },
        )
        BasicTextField(
            value = block.text,
            onValueChange = { editor.update(editor.doc.replace(editor.latest(block).copy(text = it))) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (block.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (block.checked) TextDecoration.LineThrough else null,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                // Enter adds the next checklist item.
                editor.insert(CheckBlock(NoteDoc.newId()), keyboard = true)
            }),
            modifier = Modifier.weight(1f).trackFocus(editor, block.id),
        )
    }
}

@Composable
private fun TableBlockView(editor: DocEditor, block: TableBlock, wide: Boolean) {
    val line = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(TAG_NOTE_TABLE)) {
        val cols = block.columnCount.coerceAtLeast(1)
        val cellWidth = max(if (wide) 120.dp else 100.dp, maxWidth / cols)
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .border(1.dp, line, RoundedCornerShape(6.dp))
        ) {
            block.rows.forEachIndexed { r, row ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    row.forEachIndexed { c, cell ->
                        BasicTextField(
                            value = cell,
                            onValueChange = { v -> editor.update(editor.doc.replace(editor.latest(block).setCell(r, c, v))) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                .background(if (r == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                                .border(0.5.dp, line)
                                .padding(8.dp)
                                .trackFocus(editor, "${block.id}@$r,$c"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnsBlockView(editor: DocEditor, block: ColumnsBlock, wide: Boolean) {
    val style = richTextStyle(TextKind.BODY)
    // Columns stay side by side on both screens; the cover screen just uses tighter gaps.
    val gap = if (wide) 10.dp else 6.dp
    val frame = Modifier
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        .padding(if (wide) 10.dp else 6.dp)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag(TAG_NOTE_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        block.columns.indices.forEach { i ->
            Box(Modifier.weight(1f).fillMaxHeight().then(frame)) {
                RichArea(editor, "${block.id}#$i", style, "Column ${i + 1}")
            }
        }
    }
}

@Composable
private fun DividerBlockView(editor: DocEditor, block: DividerBlock) {
    val selected = editor.focused == block.id
    Box(
        Modifier
            .fillMaxWidth()
            .clickable { editor.focused = block.id }
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .padding(vertical = 12.dp)
    ) {
        HorizontalDivider(thickness = 2.dp)
    }
}

/** A chip that opens a menu. */
@Composable
private fun FormatMenu(label: String, tag: String, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = false, onClick = { open = true }, label = { Text("$label ▾") }, modifier = Modifier.testTag(tag))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { content { open = false } }
    }
}

@Composable
private fun StyleToggle(label: String, style: TextStyle, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = style.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)))
        }
    }
}

const val TAG_FORMAT_INSERT = "format-insert"

@Composable
private fun FormattingBar(editor: DocEditor) {
    val focusManager = LocalFocusManager.current
    var open by rememberSaveable { mutableStateOf(true) }
    val rich = editor.activeRich
    val span = rich?.currentSpanStyle
    val blockId = editor.focusedBlockId
    val focusedBlock = blockId?.let { id -> editor.doc.blocks.firstOrNull { it.id == id } }

    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().testTag(TAG_FORMAT_BAR)) {
        if (!open) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { open = true }) { Text("Aa  Format") }
            }
            return@Surface
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Paragraph style.
            FormatMenu("Aa", "format-style") { close ->
                TextKind.entries.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(kind.label, style = richTextStyle(kind)) },
                        enabled = focusedBlock is TextBlock,
                        onClick = {
                            (focusedBlock as? TextBlock)?.let { editor.update(editor.doc.replace(editor.latest(it).copy(kind = kind))) }
                            close()
                        },
                    )
                }
            }
            val b = MaterialTheme.typography.titleMedium
            StyleToggle("B", b.copy(fontWeight = FontWeight.Bold), span?.fontWeight == FontWeight.Bold, rich != null) {
                rich?.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
            }
            StyleToggle("I", b.copy(fontStyle = FontStyle.Italic), span?.fontStyle == FontStyle.Italic, rich != null) {
                rich?.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
            }
            StyleToggle("U", b.copy(textDecoration = TextDecoration.Underline), span?.textDecoration?.contains(TextDecoration.Underline) == true, rich != null) {
                rich?.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
            }
            StyleToggle("S", b.copy(textDecoration = TextDecoration.LineThrough), span?.textDecoration?.contains(TextDecoration.LineThrough) == true, rich != null) {
                rich?.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
            }
            FormatMenu("Colour", "format-colour") { close ->
                TEXT_COLORS.forEach { (name, color) ->
                    DropdownMenuItem(
                        text = { Text(name, color = color) },
                        leadingIcon = { Box(Modifier.size(16.dp).background(color, CircleShape)) },
                        enabled = rich != null,
                        onClick = { rich?.addSpanStyle(SpanStyle(color = color)); close() },
                    )
                }
                DropdownMenuItem(text = { Text("Default colour") }, enabled = rich != null, onClick = {
                    rich?.let { r -> r.removeSpanStyle(SpanStyle(color = r.currentSpanStyle.color)) }; close()
                })
                HorizontalDivider()
                HIGHLIGHTS.forEach { (name, color) ->
                    DropdownMenuItem(
                        text = { Text("Highlight $name") },
                        leadingIcon = { Box(Modifier.size(16.dp).background(color, CircleShape)) },
                        enabled = rich != null,
                        onClick = { rich?.addSpanStyle(SpanStyle(background = color)); close() },
                    )
                }
                DropdownMenuItem(text = { Text("No highlight") }, enabled = rich != null, onClick = {
                    rich?.let { r -> r.removeSpanStyle(SpanStyle(background = r.currentSpanStyle.background)) }; close()
                })
            }
            FormatMenu("Lists", "format-lists") { close ->
                DropdownMenuItem(text = { Text("• Bullets") }, enabled = rich != null, onClick = { rich?.toggleUnorderedList(); close() })
                DropdownMenuItem(text = { Text("1. Numbers") }, enabled = rich != null, onClick = { rich?.toggleOrderedList(); close() })
                DropdownMenuItem(
                    text = { Text("☐ Checklist") },
                    onClick = { focusManager.clearFocus(); editor.insert(CheckBlock(NoteDoc.newId())); close() },
                    modifier = Modifier.testTag("insert-check"),
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Indent") }, enabled = rich?.canIncreaseListLevel == true, onClick = { rich?.increaseListLevel(); close() })
                DropdownMenuItem(text = { Text("Outdent") }, enabled = rich?.canDecreaseListLevel == true, onClick = { rich?.decreaseListLevel(); close() })
            }
            FormatMenu("Insert", TAG_FORMAT_INSERT) { close ->
                DropdownMenuItem(
                    text = { Text("Table") },
                    onClick = { val t = TableBlock.blank(); focusManager.clearFocus(); editor.insert(t, "${t.id}@0,0"); close() },
                    modifier = Modifier.testTag("insert-table"),
                )
                DropdownMenuItem(
                    text = { Text("2 columns") },
                    onClick = { val c = ColumnsBlock(NoteDoc.newId(), listOf("", "")); focusManager.clearFocus(); editor.insert(c, "${c.id}#0"); close() },
                    modifier = Modifier.testTag("insert-columns"),
                )
                DropdownMenuItem(
                    text = { Text("3 columns") },
                    onClick = { val c = ColumnsBlock(NoteDoc.newId(), listOf("", "", "")); focusManager.clearFocus(); editor.insert(c, "${c.id}#0"); close() },
                )
                DropdownMenuItem(text = { Text("Divider") }, onClick = { focusManager.clearFocus(); editor.insert(DividerBlock(NoteDoc.newId())); close() })
                DropdownMenuItem(text = { Text("Text block") }, onClick = { focusManager.clearFocus(); editor.insert(TextBlock(NoteDoc.newId())); close() })
            }
            if (focusedBlock is TableBlock) {
                val (r, c) = editor.focused!!.substringAfter('@').split(',').map { it.toIntOrNull() ?: 0 }.let { it[0] to it.getOrElse(1) { 0 } }
                FormatMenu("Table", "format-table") { close ->
                    val t = { editor.latest(focusedBlock) }
                    DropdownMenuItem(text = { Text("Add row below") }, onClick = { editor.update(editor.doc.replace(t().addRow(r))); close() })
                    DropdownMenuItem(text = { Text("Add column right") }, onClick = { editor.update(editor.doc.replace(t().addColumn(c))); close() })
                    DropdownMenuItem(text = { Text("Delete row") }, onClick = { editor.update(editor.doc.replace(t().removeRow(r))); close() })
                    DropdownMenuItem(text = { Text("Delete column") }, onClick = { editor.update(editor.doc.replace(t().removeColumn(c))); close() })
                }
            }
            FormatMenu("Block", "format-block") { close ->
                DropdownMenuItem(text = { Text("Move up") }, enabled = blockId != null, onClick = { blockId?.let { editor.update(editor.doc.move(it, -1)) }; close() })
                DropdownMenuItem(text = { Text("Move down") }, enabled = blockId != null, onClick = { blockId?.let { editor.update(editor.doc.move(it, 1)) }; close() })
                DropdownMenuItem(text = { Text("Delete block") }, enabled = blockId != null, onClick = {
                    blockId?.let { editor.update(editor.doc.remove(it)) }
                    editor.focused = null
                    close()
                })
            }
            VerticalDivider(Modifier.height(24.dp))
            TextButton(onClick = { open = false }) { Text("Hide") }
        }
    }
}
