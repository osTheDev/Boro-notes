package com.boro.notes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.boro.notes.notes.Block
import com.boro.notes.notes.BoxFill
import com.boro.notes.notes.CheckBlock
import com.boro.notes.notes.ColumnsBlock
import com.boro.notes.notes.DividerBlock
import com.boro.notes.notes.NoteDoc
import com.boro.notes.notes.Paper
import com.boro.notes.notes.PaperColor
import com.boro.notes.notes.PaperPattern
import com.boro.notes.notes.TableBlock
import com.boro.notes.notes.TextBlock
import com.boro.notes.notes.TextKind

private val TEXT_COLORS = listOf(
    "Red" to Color(0xFFE53935),
    "Orange" to Color(0xFFFB8C00),
    "Gold" to Color(0xFFC9A227),
    "Green" to Color(0xFF43A047),
    "Blue" to Color(0xFF1E88E5),
    "Indigo" to Color(0xFF3949AB),
    "Purple" to Color(0xFF8E24AA),
    "Brown" to Color(0xFF795548),
)

private val HIGHLIGHTS = listOf(
    "Yellow" to Color(0x66FFEB3B),
    "Green" to Color(0x6666BB6A),
    "Blue" to Color(0x6642A5F5),
    "Pink" to Color(0x66F06292),
    "Orange" to Color(0x66FFA726),
    "Purple" to Color(0x66AB47BC),
)

/**
 * The formatting toolbar: icon tools in colour-coded groups (text, lists, insert, page), plus a
 * second row for the selected table, columns or other block. Collapses to a small Format button.
 */
@Composable
internal fun FormattingBar(
    editor: DocEditor,
    /** On the inner screen the bar sits at the top and doubles as the app bar. */
    atTop: Boolean = false,
    /** App bar items around the tools when [atTop] (back button; full screen, delete). */
    start: @Composable () -> Unit = {},
    end: @Composable () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var open by rememberSaveable { mutableStateOf(true) }
    val rich = editor.activeRich
    val span = rich?.currentSpanStyle
    val blockId = editor.focusedBlockId
    val focusedBlock = blockId?.let { id -> editor.doc.blocks.firstOrNull { it.id == id } }
    val cs = MaterialTheme.colorScheme

    fun insert(block: Block, focusKey: String = block.id) {
        focusManager.clearFocus()
        editor.insert(block, focusKey)
    }

    val showPill: @Composable () -> Unit = {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(cs.primaryContainer)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("format-show"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.TextFormat, contentDescription = null, tint = cs.onPrimaryContainer)
            Spacer(Modifier.width(6.dp))
            Text("Format", color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
        }
    }
    val blockRow: @Composable () -> Unit = {
        if (open && focusedBlock != null && focusedBlock !is TextBlock) {
            if (atTop) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            BlockToolsRow(editor, focusedBlock)
            if (!atTop) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
        }
    }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = if (atTop) 2.dp else 6.dp,
        modifier = Modifier.fillMaxWidth().testTag(TAG_FORMAT_BAR),
    ) {
      Column {
        if (!atTop) blockRow()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          start()
          Box(Modifier.weight(1f)) {
            if (!open) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = if (atTop) Arrangement.Start else Arrangement.End,
                ) { showPill() }
            } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Text: paragraph style, B I U S, colour, highlight.
                ToolGroup(cs.primaryContainer, cs.onPrimaryContainer) {
                    ToolMenu(Icons.Filled.TextFields, "Text style", "format-style") { close ->
                        TextKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(kind.label, style = richTextStyle(kind)) },
                                enabled = focusedBlock is TextBlock,
                                trailingIcon = { if ((focusedBlock as? TextBlock)?.kind == kind) Icon(Icons.Filled.Check, null) },
                                onClick = {
                                    (focusedBlock as? TextBlock)?.let { editor.update(editor.doc.replace(editor.latest(it).copy(kind = kind))) }
                                    close()
                                },
                            )
                        }
                    }
                    ToolButton(Icons.Filled.FormatBold, "Bold", "format-bold", enabled = rich != null, selected = span?.fontWeight == FontWeight.Bold) {
                        rich?.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    }
                    ToolButton(Icons.Filled.FormatItalic, "Italic", "format-italic", enabled = rich != null, selected = span?.fontStyle == FontStyle.Italic) {
                        rich?.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    }
                    ToolButton(
                        Icons.Filled.FormatUnderlined, "Underline", "format-underline", enabled = rich != null,
                        selected = span?.textDecoration?.contains(TextDecoration.Underline) == true,
                    ) { rich?.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }
                    ToolButton(
                        Icons.Filled.FormatStrikethrough, "Strikethrough", "format-strike", enabled = rich != null,
                        selected = span?.textDecoration?.contains(TextDecoration.LineThrough) == true,
                    ) { rich?.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) }
                    val textColor = span?.color?.takeIf { it != Color.Unspecified }
                    ToolMenu(Icons.Filled.FormatColorText, "Text colour", "format-colour", enabled = rich != null, accent = textColor) { close ->
                        MenuLabel("Text colour")
                        SwatchGrid(
                            TEXT_COLORS.map { (name, c) -> Swatch(name, c, "colour-${name.lowercase()}", textColor == c) },
                        ) { rich?.addSpanStyle(SpanStyle(color = it.color)); close() }
                        DropdownMenuItem(
                            text = { Text("Default colour") },
                            leadingIcon = { Icon(Icons.Filled.FormatColorReset, null) },
                            onClick = { rich?.let { r -> r.removeSpanStyle(SpanStyle(color = r.currentSpanStyle.color)) }; close() },
                        )
                    }
                    val highlight = span?.background?.takeIf { it != Color.Unspecified && it != Color.Transparent }
                    ToolMenu(Icons.Filled.Highlight, "Highlight", "format-highlight", enabled = rich != null, accent = highlight) { close ->
                        MenuLabel("Highlight")
                        SwatchGrid(
                            HIGHLIGHTS.map { (name, c) -> Swatch(name, c, "highlight-${name.lowercase()}", highlight == c) },
                        ) { rich?.addSpanStyle(SpanStyle(background = it.color)); close() }
                        DropdownMenuItem(
                            text = { Text("No highlight") },
                            leadingIcon = { Icon(Icons.Filled.FormatColorReset, null) },
                            onClick = { rich?.let { r -> r.removeSpanStyle(SpanStyle(background = r.currentSpanStyle.background)) }; close() },
                        )
                    }
                }
                // Lists.
                ToolGroup(cs.secondaryContainer, cs.onSecondaryContainer) {
                    ToolButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullets", "format-bullets", enabled = rich != null, selected = rich?.isUnorderedList == true) {
                        rich?.toggleUnorderedList()
                    }
                    ToolButton(Icons.Filled.FormatListNumbered, "Numbers", "format-numbers", enabled = rich != null, selected = rich?.isOrderedList == true) {
                        rich?.toggleOrderedList()
                    }
                    ToolButton(Icons.Filled.CheckBox, "Checklist", "insert-check") { insert(CheckBlock(NoteDoc.newId())) }
                    ToolButton(Icons.AutoMirrored.Filled.FormatIndentDecrease, "Outdent", "format-outdent", enabled = rich?.canDecreaseListLevel == true) {
                        rich?.decreaseListLevel()
                    }
                    ToolButton(Icons.AutoMirrored.Filled.FormatIndentIncrease, "Indent", "format-indent", enabled = rich?.canIncreaseListLevel == true) {
                        rich?.increaseListLevel()
                    }
                }
                // Insert blocks.
                ToolGroup(cs.tertiaryContainer, cs.onTertiaryContainer) {
                    ToolButton(Icons.Filled.TableChart, "Insert table", "insert-table") {
                        val t = TableBlock.blank(); insert(t, "${t.id}@0,0")
                    }
                    ToolButton(Icons.Filled.ViewColumn, "Insert 2 columns", "insert-columns") {
                        val c = ColumnsBlock(NoteDoc.newId(), listOf("", "")); insert(c, "${c.id}#0")
                    }
                    ToolButton(Icons.Filled.ViewWeek, "Insert 3 columns", "insert-columns-3") {
                        val c = ColumnsBlock(NoteDoc.newId(), listOf("", "", "")); insert(c, "${c.id}#0")
                    }
                    ToolButton(Icons.Filled.HorizontalRule, "Insert divider", "insert-divider") { insert(DividerBlock(NoteDoc.newId())) }
                }
                // Page look.
                ToolGroup(cs.surfaceVariant, cs.onSurfaceVariant) {
                    ToolMenu(Icons.Filled.Palette, "Page style", "format-page", accent = editor.doc.paper.color.argb?.let { Color(it) }) { close ->
                        PageMenu(editor.doc.paper) { editor.update(editor.doc.copy(paper = it)); close() }
                    }
                }
                ToolButton(Icons.Filled.KeyboardArrowDown, "Hide formatting", "format-hide") { open = false }
            }
            }
          }
          end()
        }
        if (atTop) blockRow()
      }
    }
}

/** Tools for the selected table, columns, checklist item or divider. */
@Composable
private fun BlockToolsRow(editor: DocEditor, block: Block) {
    val cs = MaterialTheme.colorScheme
    val (label, icon) = when (block) {
        is TableBlock -> "Table" to Icons.Filled.TableChart
        is ColumnsBlock -> "Columns" to Icons.Filled.ViewColumn
        is DividerBlock -> "Divider" to Icons.Filled.HorizontalRule
        is CheckBlock -> "Checklist" to Icons.Filled.CheckBox
        is TextBlock -> "Text" to Icons.Filled.TextFields
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(cs.inverseSurface).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = cs.inverseOnSurface, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = cs.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
        }
        if (block is TableBlock || block is ColumnsBlock) {
            val current = (block as? TableBlock)?.fill ?: (block as? ColumnsBlock)?.fill
            ToolGroup(cs.tertiaryContainer, cs.onTertiaryContainer) {
                ToolMenu(Icons.Filled.FormatColorFill, "Fill colour", "format-fill", accent = current?.let { it.argb?.let { Color(it) } ?: cs.surface }) { close ->
                    MenuLabel("Fill (hides the page lines)")
                    val swatches = listOf(Swatch("No fill", Color.Transparent, "fill-none", current == null)) +
                        BoxFill.entries.map { f -> Swatch(f.label, f.argb?.let { Color(it) } ?: cs.surface, "fill-${f.name.lowercase()}", current == f) }
                    SwatchGrid(swatches) { s ->
                        val fill = BoxFill.entries.firstOrNull { "fill-${it.name.lowercase()}" == s.tag }
                        val b = editor.latest(block)
                        editor.update(editor.doc.replace(if (b is TableBlock) b.copy(fill = fill) else (b as ColumnsBlock).copy(fill = fill)))
                        close()
                    }
                }
            }
        }
        if (block is TableBlock) {
            val (r, c) = editor.focused?.substringAfter('@', "")?.split(',')?.map { it.toIntOrNull() ?: 0 }
                ?.let { it.getOrElse(0) { 0 } to it.getOrElse(1) { 0 } } ?: (0 to 0)
            val t = { editor.latest(block) }
            ToolGroup(cs.secondaryContainer, cs.onSecondaryContainer) {
                LabeledTool(Icons.Filled.Add, "Row", "table-add-row") { editor.update(editor.doc.replace(t().addRow(r))) }
                LabeledTool(Icons.Filled.Add, "Column", "table-add-column") { editor.update(editor.doc.replace(t().addColumn(c))) }
                LabeledTool(Icons.Filled.Remove, "Row", "table-remove-row") { editor.update(editor.doc.replace(t().removeRow(r))) }
                LabeledTool(Icons.Filled.Remove, "Column", "table-remove-column") { editor.update(editor.doc.replace(t().removeColumn(c))) }
            }
        }
        ToolGroup(cs.surfaceVariant, cs.onSurfaceVariant) {
            ToolButton(Icons.Filled.ArrowUpward, "Move up", "block-up") { editor.update(editor.doc.move(block.id, -1)) }
            ToolButton(Icons.Filled.ArrowDownward, "Move down", "block-down") { editor.update(editor.doc.move(block.id, 1)) }
        }
        ToolGroup(cs.errorContainer, cs.onErrorContainer) {
            ToolButton(Icons.Filled.Delete, "Delete $label", "block-delete") {
                editor.update(editor.doc.remove(block.id))
                editor.focused = null
            }
        }
    }
}

/** Paper colour swatches, line pattern previews and the margin switch. */
@Composable
private fun PageMenu(paper: Paper, onChange: (Paper) -> Unit) {
    val cs = MaterialTheme.colorScheme
    MenuLabel("Paper")
    SwatchGrid(
        PaperColor.entries.map { c -> Swatch(c.label, c.argb?.let { Color(it) } ?: cs.surface, "paper-${c.name.lowercase()}", paper.color == c) },
    ) { s -> PaperColor.entries.firstOrNull { "paper-${it.name.lowercase()}" == s.tag }?.let { onChange(paper.copy(color = it)) } }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    MenuLabel("Lines")
    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PaperPattern.entries.forEach { pt ->
            val selected = paper.pattern == pt
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PatternPreview(
                    pt,
                    selected,
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onChange(paper.copy(pattern = pt)) }
                        .testTag("pattern-${pt.name.lowercase()}")
                        .semantics { contentDescription = pt.label },
                )
                Text(pt.label.substringBefore(' '), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    DropdownMenuItem(
        text = { Text("Margin line") },
        trailingIcon = { Switch(checked = paper.margin, onCheckedChange = null) },
        onClick = { onChange(paper.copy(margin = !paper.margin)) },
        modifier = Modifier.testTag("paper-margin"),
    )
}

@Composable
private fun PatternPreview(pattern: PaperPattern, selected: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val ink = cs.onSurface
    Canvas(
        modifier
            .size(44.dp)
            .background(cs.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        val step = size.width / 4
        val line = ink.copy(alpha = 0.35f)
        val w = 1.dp.toPx()
        when (pattern) {
            PaperPattern.PLAIN -> Unit
            PaperPattern.RULED -> for (i in 1..3) drawLine(line, Offset(4f, i * step), Offset(size.width - 4f, i * step), w)
            PaperPattern.GRID -> for (i in 1..3) {
                drawLine(line, Offset(0f, i * step), Offset(size.width, i * step), w)
                drawLine(line, Offset(i * step, 0f), Offset(i * step, size.height), w)
            }
            PaperPattern.GRAPH -> for (i in 1..7) {
                val a = if (i % 2 == 0) line else ink.copy(alpha = 0.15f)
                drawLine(a, Offset(0f, i * step / 2), Offset(size.width, i * step / 2), w)
                drawLine(a, Offset(i * step / 2, 0f), Offset(i * step / 2, size.height), w)
            }
            PaperPattern.DOTS -> for (x in 1..3) for (y in 1..3) drawCircle(ink.copy(alpha = 0.5f), 1.6.dp.toPx(), Offset(x * step, y * step))
        }
    }
}

/** Colour choices open as a spiralling paint wheel (see [PaletteWheel]). */
@Composable
private fun SwatchGrid(swatches: List<Swatch>, onPick: (Swatch) -> Unit) = PaletteWheel(swatches, onPick)

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** A rounded tinted group of tools, so related tools read as one at a glance. */
@Composable
private fun ToolGroup(container: Color, content: Color, tools: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(container).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) { tools() }
    }
}

/** An icon tool. [selected] shows it's on (e.g. bold); [accent] draws a colour bar under it. */
@Composable
private fun ToolButton(
    icon: ImageVector,
    description: String,
    tag: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val tint = LocalContentColor.current
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(42.dp)
            .bounce(source)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) tint.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint.copy(alpha = if (enabled) 1f else 0.35f))
        if (accent != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(width = 18.dp, height = 3.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** A small tool with an icon and a word, for actions an icon alone can't say (add row vs column). */
@Composable
private fun LabeledTool(icon: ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(2.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current)
    }
}

/** An icon tool that opens a menu. */
@Composable
private fun ToolMenu(
    icon: ImageVector,
    description: String,
    tag: String,
    enabled: Boolean = true,
    accent: Color? = null,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolButton(icon, description, tag, enabled = enabled, selected = open, accent = accent) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { content { open = false } }
    }
}
