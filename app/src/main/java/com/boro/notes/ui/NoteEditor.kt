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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import com.boro.notes.notes.PaperPattern
import com.boro.notes.notes.PaperColor
import com.boro.notes.notes.Paper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.boro.notes.notes.BoxFill
import com.boro.notes.notes.CheckBlock
import com.boro.notes.notes.ColumnsBlock
import com.boro.notes.notes.DividerBlock
import com.boro.notes.notes.DrawingBlock
import com.boro.notes.notes.ImageBlock
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
const val TAG_NOTE_BLANK = "noteBlank"
const val TAG_NOTE_PAPER = "notePaper"


/**
 * The formatted note body: rich text, checklists, tables, columns and dividers, with a compact
 * formatting bar. Reports every change (debounced) as document JSON plus plain text.
 */
@Composable
fun NoteEditor(
    note: EditorState,
    onChange: (content: String, plain: String) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    /** Shown above the body on the same paper (the title field). */
    header: @Composable () -> Unit = {},
    /** Put the formatting bar at the top, holding [barStart] and [barEnd] (inner screen). */
    toolbarAtTop: Boolean = false,
    barStart: @Composable () -> Unit = {},
    barEnd: @Composable () -> Unit = {},
) {
    // The editor owns the document once a note is open; reload only when another note opens.
    key(note.id) {
        NoteEditorContent(note, onChange, wide, modifier, header, toolbarAtTop, barStart, barEnd)
    }
}

/** Editor state for one open note: the document plus a rich text state per text area. */
internal class DocEditor(initial: NoteDoc) {
    var doc by mutableStateOf(initial)
        private set
    /** Rich text areas keyed by block id, or "blockId#column" for columns. */
    val rich = HashMap<String, RichTextState>()
    /** What has focus: a block id, "id#column" or "id@row,col" for a table cell. */
    var focused by mutableStateOf<String?>(null)
    /** A block to focus as soon as it's on screen (e.g. a new checklist item). */
    var pendingFocus by mutableStateOf<String?>(null)
    /** Pen for drawing blocks. */
    var inkColor by mutableStateOf(INK_COLORS[0])
    var inkWidth by mutableStateOf(3f)
    var eraser by mutableStateOf(false)

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
            is ImageBlock -> b.caption
            is DrawingBlock -> b.caption
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

    /** Where typing continues at the end of [b], or null for a block you can't type in. */
    fun endKey(b: Block): String? = when (b) {
        is TextBlock, is CheckBlock -> b.id
        is ColumnsBlock -> "${b.id}#${b.columns.lastIndex}"
        is TableBlock -> "${b.id}@${b.rowCount - 1},${b.columnCount - 1}"
        is DividerBlock, is ImageBlock, is DrawingBlock -> null
    }

    /** Moves the cursor (and keyboard) to the end of [key]. */
    fun focusEnd(key: String) {
        rich[key]?.let { it.selection = TextRange(it.annotatedString.text.length) }
        pendingFocus = key
    }

    /** Tapping the blank space under the note: continue typing on the last line. */
    fun focusLast() {
        doc.blocks.lastOrNull()?.let(::endKey)?.let(::focusEnd)
    }

    /**
     * Backspace in an empty line or checklist item: the blank goes away and the cursor moves to
     * the end of what's above. Right under a divider, the divider goes instead.
     * Returns false when there's nothing to remove, so the key is handled as usual.
     */
    fun backspaceEmpty(b: Block): Boolean {
        val i = doc.indexOf(b.id)
        if (i <= 0) return false
        val above = doc.blocks[i - 1]
        if (above is DividerBlock) {
            update(doc.remove(above.id))
            return true
        }
        val target = doc.blocks.take(i).asReversed().firstNotNullOfOrNull(::endKey) ?: return false
        val isLast = i == doc.blocks.lastIndex
        // The last line under a table or columns is always kept, so there's somewhere to type.
        if (!(isLast && b is TextBlock && above !is TextBlock)) {
            update(doc.remove(b.id))
            rich.remove(b.id)
        }
        focusEnd(target)
        return true
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun NoteEditorContent(
    note: EditorState,
    onChange: (String, String) -> Unit,
    wide: Boolean,
    modifier: Modifier,
    header: @Composable () -> Unit,
    toolbarAtTop: Boolean,
    barStart: @Composable () -> Unit,
    barEnd: @Composable () -> Unit,
) {
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

    val paper = editor.doc.paper
    val lined = paper.pattern != PaperPattern.PLAIN
    Column(modifier) {
        if (toolbarAtTop) FormattingBar(editor, atTop = true, start = barStart, end = barEnd)
        PaperTheme(paper) {
          // The page is at least as tall as the screen, so paper and lines always reach the bottom.
          BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageHeight = maxHeight
            Column(
                Modifier
                    .fillMaxSize()
                    .background(paper.color.argb?.let { Color(it) } ?: Color.Transparent)
                    .verticalScroll(rememberScrollState())
                    .testTag(TAG_NOTE_PAPER),
            ) {
                header()
                val lineHeight = with(LocalDensity.current) { richTextStyle(TextKind.BODY).lineHeight.toDp() }
                val start = if (paper.margin) 40.dp else 16.dp
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = pageHeight)
                        .paperLines(paper, lineHeight, top = 8.dp, marginX = if (paper.margin) 30.dp else null)
                        .padding(start = start, end = 16.dp, top = 8.dp, bottom = 8.dp)
                        .testTag(TAG_NOTE_EDITOR),
                    // On lined paper, text blocks sit flush so they stay on the lines.
                    verticalArrangement = Arrangement.spacedBy(if (lined) 0.dp else 6.dp),
                ) {
                    editor.doc.blocks.forEachIndexed { index, block ->
                        key(block.id) {
                            when (block) {
                                is TextBlock -> TextBlockView(editor, block, placeholder = index == 0 && editor.doc.blocks.size == 1)
                                is CheckBlock -> CheckBlockView(editor, block)
                                is TableBlock -> TableBlockView(editor, block, wide)
                                is ColumnsBlock -> ColumnsBlockView(editor, block, wide)
                                is DividerBlock -> DividerBlockView(editor, block)
                                is ImageBlock -> ImageBlockView(editor, block)
                                is DrawingBlock -> DrawingBlockView(editor, block)
                            }
                        }
                    }
                    // Blank space under the note, down to the bottom of the page and a little past it
                    // (so the last line can scroll up from behind the keyboard). Tap it to keep typing.
                    val tapToType = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { editor.focusLast() }
                    Box(Modifier.fillMaxWidth().weight(1f).then(tapToType).testTag(TAG_NOTE_BLANK))
                    Box(Modifier.fillMaxWidth().height(lineHeight * 6).then(tapToType))
                }
            }
          }
        }
        if (!toolbarAtTop) FormattingBar(editor)
    }
}

/** Recolours the note's content for its paper; the default paper follows the app theme. */
@Composable
private fun PaperTheme(paper: Paper, content: @Composable () -> Unit) {
    val argb = paper.color.argb
    if (argb == null) {
        content()
        return
    }
    val cs = MaterialTheme.colorScheme
    val ink = if (paper.color.dark) Color(0xFFF1EEE8) else Color(0xFF1F1B16)
    MaterialTheme(
        colorScheme = cs.copy(
            surface = Color(argb),
            onSurface = ink,
            onSurfaceVariant = ink.copy(alpha = 0.6f),
            outline = ink.copy(alpha = 0.45f),
            outlineVariant = ink.copy(alpha = 0.25f),
            surfaceContainerHigh = ink.copy(alpha = 0.07f),
            primary = if (paper.color.dark) Color(0xFFB8C7FF) else Color(0xFF3B5BA9),
        ),
        content = content,
    )
}

/**
 * Draws the paper's lines behind the note. Ruled lines fall at the bottom of each body text line
 * ([lineHeight] apart, starting [top] down), so writing sits on them like a notebook.
 */
@Composable
private fun Modifier.paperLines(paper: Paper, lineHeight: Dp, top: Dp, marginX: Dp?): Modifier {
    val ink = MaterialTheme.colorScheme.onSurface
    val marginColor = Color(0xFFE57373)
    return drawBehind {
        val step = lineHeight.toPx()
        val y0 = top.toPx()
        val thin = 1.dp.toPx()
        val line = ink.copy(alpha = 0.14f)
        fun horizontal(gap: Float, color: Color) {
            var y = y0 + gap
            while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), thin); y += gap }
        }
        fun vertical(gap: Float, color: Color) {
            var x = gap
            while (x < size.width) { drawLine(color, Offset(x, y0), Offset(x, size.height), thin); x += gap }
        }
        when (paper.pattern) {
            PaperPattern.PLAIN -> Unit
            PaperPattern.RULED -> horizontal(step, line)
            PaperPattern.GRID -> { horizontal(step, line); vertical(step, line) }
            PaperPattern.GRAPH -> {
                horizontal(step / 2, ink.copy(alpha = 0.07f)); vertical(step / 2, ink.copy(alpha = 0.07f))
                horizontal(step * 2, line); vertical(step * 2, line)
            }
            PaperPattern.DOTS -> {
                val r = 1.4.dp.toPx()
                var y = y0 + step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) { drawCircle(ink.copy(alpha = 0.3f), r, Offset(x, y)); x += step }
                    y += step
                }
            }
        }
        marginX?.let { m -> drawLine(marginColor.copy(alpha = 0.6f), Offset(m.toPx(), 0f), Offset(m.toPx(), size.height), thin) }
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
internal fun richTextStyle(kind: TextKind): TextStyle {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RichArea(
    editor: DocEditor,
    key: String,
    style: TextStyle,
    placeholder: String?,
    modifier: Modifier = Modifier,
    /** Called on backspace while the area is empty; return true if handled. */
    onEmptyBackspace: (() -> Boolean)? = null,
    /** Space inside the field (still tappable), e.g. for boxed columns. */
    contentPadding: Dp = 0.dp,
) {
    val state = editor.rich[key] ?: return
    // Keep the cursor on screen while typing: the page scrolls as text grows past the bottom.
    val bringIntoView = remember { BringIntoViewRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val lineGap = with(LocalDensity.current) { 32.dp.toPx() }
    LaunchedEffect(state, editor.focused == key) {
        if (editor.focused != key) return@LaunchedEffect
        snapshotFlow { Triple(state.selection, state.annotatedString.text.length, layout) }
            .collect { (sel, _, l) ->
                if (l == null) return@collect
                val offset = sel.end.coerceIn(0, l.layoutInput.text.length)
                val r = runCatching { l.getCursorRect(offset) }.getOrNull() ?: return@collect
                bringIntoView.bringIntoView(Rect(r.left, r.top, r.right + 1f, r.bottom + lineGap))
            }
    }
    BasicRichTextEditor(
        state = state,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        onTextLayout = { layout = it },
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .onEmptyBackspace({ state.annotatedString.text.isEmpty() }, onEmptyBackspace)
            .trackFocus(editor, key),
        decorationBox = { inner ->
            Box(Modifier.padding(contentPadding)) {
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
        // Bar drawn behind rather than measured, so the rich editor never gets intrinsic measuring.
        val bar = MaterialTheme.colorScheme.primary
        RichArea(
            editor, block.id, style, hint,
            onEmptyBackspace = { editor.backspaceEmpty(editor.latest(block)) },
            modifier = Modifier
                .drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
                .padding(start = 15.dp),
        )
    } else {
        RichArea(editor, block.id, style, hint, onEmptyBackspace = { editor.backspaceEmpty(editor.latest(block)) })
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
            modifier = Modifier
                .weight(1f)
                .onEmptyBackspace({ editor.latest(block).text.isEmpty() }) { editor.backspaceEmpty(editor.latest(block)) }
                .trackFocus(editor, block.id),
        )
    }
}

/** Runs [onBackspace] when backspace is pressed while [isEmpty]; other keys pass through. */
private fun Modifier.onEmptyBackspace(isEmpty: () -> Boolean, onBackspace: (() -> Boolean)?): Modifier =
    if (onBackspace == null) this else onPreviewKeyEvent { e ->
        e.type == KeyEventType.KeyDown && e.key == Key.Backspace && isEmpty() && onBackspace()
    }

@Composable
private fun TableBlockView(editor: DocEditor, block: TableBlock, wide: Boolean) {
  FillTheme(block.fill) { fillColor ->
    val line = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(TAG_NOTE_TABLE)) {
        val cols = block.columnCount.coerceAtLeast(1)
        val cellWidth = max(if (wide) 120.dp else 100.dp, maxWidth / cols)
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .background(fillColor, RoundedCornerShape(6.dp))
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
                            // Padding inside the field, so a tap anywhere in the cell lands in it.
                            decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(8.dp)) { inner() } },
                            modifier = Modifier
                                .width(cellWidth)
                                .fillMaxHeight()
                                .heightIn(min = 40.dp)
                                .background(if (r == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                                .border(0.5.dp, line)
                                .trackFocus(editor, "${block.id}@$r,$c"),
                        )
                    }
                }
            }
        }
    }
  }
}

/**
 * Gives a table or columns block its fill: null keeps it see-through (paper lines show), a fill
 * paints it solid. Light fills switch to dark text so it stays readable on dark paper.
 */
@Composable
private fun FillTheme(fill: BoxFill?, content: @Composable (Color) -> Unit) {
    val argb = fill?.argb
    when {
        fill == null -> content(Color.Transparent)
        argb == null -> content(MaterialTheme.colorScheme.surface)
        else -> {
            val ink = Color(0xFF1F1B16)
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    surface = Color(argb),
                    onSurface = ink,
                    onSurfaceVariant = ink.copy(alpha = 0.6f),
                    outline = ink.copy(alpha = 0.45f),
                    outlineVariant = ink.copy(alpha = 0.25f),
                    surfaceContainerHigh = ink.copy(alpha = 0.07f),
                    primary = Color(0xFF3B5BA9),
                ),
            ) { content(Color(argb)) }
        }
    }
}

@Composable
private fun ColumnsBlockView(editor: DocEditor, block: ColumnsBlock, wide: Boolean) {
    val style = richTextStyle(TextKind.BODY)
    // Columns stay side by side on both screens; the cover screen just uses tighter gaps.
    val gap = if (wide) 10.dp else 6.dp
    val shape = RoundedCornerShape(8.dp)
    FillTheme(block.fill) { fillColor ->
        // No intrinsic height here: the rich text editor keeps re-laying out under intrinsic measuring.
        Row(
            Modifier.fillMaxWidth().testTag(TAG_NOTE_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            block.columns.indices.forEach { i ->
                // The whole box is the text field, so a tap anywhere in it starts typing.
                RichArea(
                    editor, "${block.id}#$i", style, "Column ${i + 1}",
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 96.dp)
                        .background(fillColor, shape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
                    contentPadding = if (wide) 10.dp else 6.dp,
                )
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

