package com.boro.notes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boro.notes.ui.theme.DarkMode
import com.boro.notes.ui.theme.HomePattern
import com.boro.notes.ui.theme.LookSettings
import com.boro.notes.ui.theme.ThemePreset
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Numbers shown on the home tiles. */
data class HomeStats(
    val notes: Int = 0,
    val designs: Int = 0,
    val punchCards: Int = 0,
    val punchedToday: Int = 0,
    val openTodos: Int = 0,
)

/** A home tile. [section] null means it's coming in a later update. */
private data class HomeTile(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val defaultColor: Int,
    val section: Section?,
)

private val TILES = listOf(
    HomeTile("NOTES", "Notes", Icons.Filled.EditNote, 0, Section.NOTES),
    HomeTile("DESIGNS", "Designs", Icons.Filled.Checkroom, 1, Section.DESIGNS),
    HomeTile("TASKS", "Tasks", Icons.Filled.TaskAlt, 2, Section.TASKS),
    HomeTile("SASHIKO", "Sashiko", Icons.Filled.Texture, 4, null),
    HomeTile("JOURNAL", "Journal", Icons.Filled.AutoStories, 3, null),
)

const val TAG_HOME = "homeScreen"
const val TAG_HOME_CUSTOMIZE = "homeCustomize"

/** The landing page: a tile for each part of the app over a faint sashiko pattern. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    stats: HomeStats,
    look: LookSettings,
    onLookChange: (LookSettings) -> Unit,
    onOpen: (Section) -> Unit,
) {
    var customizing by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(cs.background).testTag(TAG_HOME)) {
        val columns = if (maxWidth >= 840.dp) 3 else 2
        val pattern = cs.primary.copy(alpha = 0.07f)
        Canvas(Modifier.fillMaxSize()) { drawHomePattern(look.pattern, pattern, 36.dp.toPx()) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = cs.onBackground)
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { customizing = true }, modifier = Modifier.testTag(TAG_HOME_CUSTOMIZE)) {
                    Icon(Icons.Filled.Palette, contentDescription = "Customise look", tint = cs.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            TILES.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { tile ->
                        Tile(
                            tile,
                            subtitle = subtitleFor(tile, stats),
                            color = PUNCH_COLORS[(look.tileColors[tile.key] ?: tile.defaultColor).mod(PUNCH_COLORS.size)],
                            onClick = { tile.section?.let(onOpen) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (customizing) {
        ModalBottomSheet(onDismissRequest = { customizing = false }) {
            CustomizeSheet(look, onLookChange)
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Hello, night owl"
}

private fun subtitleFor(tile: HomeTile, s: HomeStats): String = when (tile.key) {
    "NOTES" -> if (s.notes == 1) "1 note" else "${s.notes} notes"
    "DESIGNS" -> if (s.designs == 1) "1 design" else "${s.designs} designs"
    "TASKS" -> buildList {
        if (s.punchCards > 0) add("${s.punchedToday}/${s.punchCards} punched today")
        add(if (s.openTodos == 1) "1 to-do" else "${s.openTodos} to-dos")
    }.joinToString(" · ")
    else -> "Coming soon"
}

/** A coloured tile with a running-stitch border, like a sashiko patch. */
@Composable
private fun Tile(tile: HomeTile, subtitle: String, color: Color, onClick: () -> Unit, modifier: Modifier) {
    val soon = tile.section == null
    Box(
        modifier
            .height(150.dp)
            .alpha(if (soon) 0.55f else 1f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(color, lerpDark(color))))
            .stitchedBorder(Color.White.copy(alpha = 0.6f), inset = 7.dp, radius = 18.dp)
            .clickable(enabled = !soon, onClick = onClick)
            .padding(18.dp)
            .testTag("tile-${tile.key.lowercase()}"),
    ) {
        Icon(tile.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp).align(Alignment.TopStart))
        if (soon) {
            Text(
                "Soon",
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(tile.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 2)
        }
    }
}

private fun lerpDark(c: Color) = androidx.compose.ui.graphics.lerp(c, Color.Black, 0.25f)

private fun Modifier.stitchedBorder(color: Color, inset: Dp, radius: Dp) = drawBehind {
    val i = inset.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(i, i),
        size = Size(size.width - 2 * i, size.height - 2 * i),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
    )
}

/** Draws a repeating sashiko pattern over the whole area, one motif per [cell]. */
fun DrawScope.drawHomePattern(pattern: HomePattern, color: Color, cell: Float) {
    val w = 1.4f * density
    when (pattern) {
        HomePattern.NONE -> Unit
        HomePattern.JUJI -> {
            // Jujizashi: small crosses on a grid.
            val arm = cell * 0.2f
            var y = cell / 2
            while (y < size.height + cell) {
                var x = cell / 2
                while (x < size.width + cell) {
                    drawLine(color, Offset(x - arm, y), Offset(x + arm, y), w)
                    drawLine(color, Offset(x, y - arm), Offset(x, y + arm), w)
                    x += cell
                }
                y += cell
            }
        }
        HomePattern.SEIGAIHA -> {
            // Seigaiha: overlapping fans of concentric arcs.
            val r = cell * 0.75f
            var row = 0
            var y = r
            while (y < size.height + r * 2) {
                var x = if (row % 2 == 0) 0f else r
                while (x < size.width + r * 2) {
                    for (k in 1..3) {
                        val rr = r * k / 3f
                        drawArc(color, 180f, 180f, false, Offset(x - rr, y - rr), Size(rr * 2, rr * 2), style = Stroke(w))
                    }
                    x += r * 2
                }
                y += r / 2
                row++
            }
        }
        HomePattern.STITCH -> {
            // Rows of running stitch, alternating offset.
            val dash = PathEffect.dashPathEffect(floatArrayOf(cell * 0.3f, cell * 0.2f), 0f)
            val dash2 = PathEffect.dashPathEffect(floatArrayOf(cell * 0.3f, cell * 0.2f), cell * 0.25f)
            var y = cell / 2
            var row = 0
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), w, pathEffect = if (row % 2 == 0) dash else dash2)
                y += cell / 2
                row++
            }
        }
        HomePattern.DOTS -> {
            var y = cell / 2
            while (y < size.height + cell) {
                var x = cell / 2
                while (x < size.width + cell) {
                    drawCircle(color, 2.2f * density, Offset(x, y))
                    x += cell / 1.5f
                }
                y += cell / 1.5f
            }
        }
    }
}

@Composable
private fun CustomizeSheet(look: LookSettings, onChange: (LookSettings) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Make it yours", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        SheetLabel("Theme")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemePreset.entries.forEach { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val selected = look.theme == t
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (t.primary == null) Brush.sweepGradient(listOf(Color(0xFFE57373), Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFBA68C8), Color(0xFFE57373)))
                                else Brush.linearGradient(listOf(t.primary, t.primary, t.tertiary!!))
                            )
                            .border(if (selected) 3.dp else 1.dp, if (selected) cs.onSurface else cs.outlineVariant, CircleShape)
                            .clickable { onChange(look.copy(theme = t)) }
                            .testTag("theme-${t.name.lowercase()}"),
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White) }
                    Text(t.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        SheetLabel("Light or dark")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DarkMode.entries.forEach { m ->
                val icon = when (m) {
                    DarkMode.AUTO -> Icons.Filled.BrightnessAuto
                    DarkMode.LIGHT -> Icons.Filled.LightMode
                    DarkMode.DARK -> Icons.Filled.DarkMode
                }
                FilterChip(
                    selected = look.mode == m,
                    onClick = { onChange(look.copy(mode = m)) },
                    label = { Text(m.label) },
                    leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("mode-${m.name.lowercase()}"),
                )
            }
        }

        SheetLabel("Home pattern")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomePattern.entries.forEach { p ->
                val selected = look.pattern == p
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val ink = cs.primary.copy(alpha = 0.5f)
                    Canvas(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceContainerHigh)
                            .border(if (selected) 3.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { onChange(look.copy(pattern = p)) }
                            .testTag("pattern-${p.name.lowercase()}")
                    ) { drawHomePattern(p, ink, 18.dp.toPx()) }
                    Text(p.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        HorizontalDivider()
        SheetLabel("Tile colours")
        TILES.forEach { tile ->
            val current = look.tileColors[tile.key] ?: tile.defaultColor
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(tile.icon, null, tint = PUNCH_COLORS[current.mod(PUNCH_COLORS.size)])
                Spacer(Modifier.width(8.dp))
                Text(tile.label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PUNCH_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(if (i == current) 3.dp else 0.dp, cs.onSurface, CircleShape)
                                .clickable { onChange(look.copy(tileColors = look.tileColors + (tile.key to i))) }
                                .testTag("tilecolor-${tile.key.lowercase()}-$i"),
                            contentAlignment = Alignment.Center,
                        ) { if (i == current) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
