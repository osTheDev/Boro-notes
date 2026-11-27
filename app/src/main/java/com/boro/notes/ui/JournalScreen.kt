package com.boro.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boro.notes.data.Journal
import com.boro.notes.data.JournalPage
import com.boro.notes.ui.theme.HomePattern
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val TAG_JOURNAL = "journalScreen"
const val TAG_NEW_JOURNAL = "newJournal"
const val TAG_CLOSED_BOOK = "closedBook"
const val TAG_OPEN_HERE = "openHere"
const val TAG_OPEN_BOOK = "openBook"
const val TAG_PAGE_LIST = "pageList"
const val TAG_PAGE_TITLE = "pageTitle"
const val TAG_TODAY_PAGE = "todayPage"
const val TAG_COVER_TITLE = "coverTitle"
const val TAG_COVER_SAVE = "coverSave"

/** Cover colours: indigo, rust, sage, ochre, plum, teal, oxblood, slate (same as the punch cards). */
private fun coverColor(i: Int) = PUNCH_COLORS[i.mod(PUNCH_COLORS.size)]

/** Cover patterns, stitched over the cloth. */
private val COVER_PATTERNS = HomePattern.entries

/**
 * Journals: a shelf of books. On the cover screen a picked book stays closed (its cover fills the
 * screen) until the phone is unfolded, then it opens to its dated pages.
 */
@Composable
fun JournalScreen(vm: JournalViewModel, wide: Boolean) {
    val journals by vm.journals.collectAsStateWithLifecycle()
    val openId by vm.openId.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    val book = journals.firstOrNull { it.id == openId }
    Box(Modifier.fillMaxSize().testTag(TAG_JOURNAL)) {
        if (book == null) {
            Shelf(journals, wide, vm)
        } else {
            BackHandler { vm.closeBook() }
            var openHere by rememberSaveable(book.id) { mutableStateOf(false) }
            if (wide || openHere) {
                OpenBook(vm, book, wide, onBack = { if (wide) vm.closeBook() else openHere = false })
            } else {
                ClosedBook(book, vm, onOpenHere = { openHere = true })
            }
        }
    }
}

/** What the cover editor is editing: a new book, or an existing one. */
private data class CoverDraft(val id: Long?, val title: String, val color: Int, val pattern: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun Shelf(journals: List<Journal>, wide: Boolean, vm: JournalViewModel) {
    var draft by remember { mutableStateOf<CoverDraft?>(null) }
    val cs = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().background(cs.background)) {
        val columns = when {
            maxWidth >= 840.dp -> 5
            maxWidth >= 600.dp -> 4
            else -> 2
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("Journals", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (journals.isEmpty()) "Start a book for field notes, days, anything worth keeping"
                    else "Tap a book to pick it · hold to change its cover",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            draft?.let { d ->
                CoverEditor(
                    d,
                    onChange = { draft = it },
                    onSave = {
                        if (d.id == null) vm.createJournal(d.title, d.color, d.pattern)
                        else journals.firstOrNull { it.id == d.id }?.let { vm.updateJournal(it.copy(title = d.title, color = d.color, pattern = d.pattern)) }
                        draft = null
                    },
                    onCancel = { draft = null },
                    onDelete = d.id?.let { id -> { vm.trashJournal(id); draft = null } },
                )
            }
            val tiles: List<Journal?> = journals + listOf(null)
            tiles.chunked(columns).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEachIndexed { c, j ->
                        val mod = Modifier.weight(1f).popIn(r * columns + c)
                        if (j == null) {
                            NewBook(mod) { draft = CoverDraft(null, "", journals.size % PUNCH_COLORS.size, 1) }
                        } else {
                            Column(mod, horizontalAlignment = Alignment.CenterHorizontally) {
                                BookCover(
                                    j.title, j.color, j.pattern,
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { vm.select(j.id) },
                                            onLongClick = { draft = CoverDraft(j.id, j.title, j.color, j.pattern) },
                                        )
                                        .testTag("book-${j.id}"),
                                )
                                Text(
                                    "Updated ${shortDate(j.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The "+ New journal" spot on the shelf: an empty book outline in running stitch. */
@Composable
private fun NewBook(modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .aspectRatio(0.72f)
            // Clickable outside the clip: hit tests on uneven rounded corners need Path ops.
            .clickable(onClick = onClick)
            .testTag(TAG_NEW_JOURNAL)
            .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp))
            .background(cs.surfaceContainerLow)
            .drawBehind {
                val i = 6.dp.toPx()
                drawRoundRect(
                    cs.outline,
                    topLeft = Offset(i, i),
                    size = Size(size.width - 2 * i, size.height - 2 * i),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Add, null, tint = cs.primary, modifier = Modifier.size(36.dp))
            Text("New journal", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
    }
}

/** Title, cloth colour and stitch pattern for a book. Inline (not a dialog) so it sits on the shelf. */
@Composable
private fun CoverEditor(
    draft: CoverDraft,
    onChange: (CoverDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        ConfirmDeleteDialog("“${draft.title.ifBlank { "Journal" }}”", onConfirm = onDelete, onDismiss = { confirmDelete = false })
    }
    Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 2.dp, color = cs.surfaceContainer) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(draft.title, draft.color, draft.pattern, Modifier.width(96.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (draft.id == null) "New journal" else "Edit cover", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onChange(draft.copy(title = it)) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag(TAG_COVER_TITLE),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PUNCH_COLORS.indices.forEach { i ->
                        val selected = draft.color == i
                        Box(
                            Modifier
                                .size(34.dp)
                                .popIn(i)
                                .clip(CircleShape)
                                .background(coverColor(i))
                                .border(if (selected) 3.dp else 0.dp, if (selected) cs.onSurface else Color.Transparent, CircleShape)
                                .clickable { onChange(draft.copy(color = i)) }
                                .testTag("cover-colour-$i"),
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COVER_PATTERNS.forEachIndexed { i, p ->
                        val selected = draft.pattern == i
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(
                                Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(coverColor(draft.color))
                                    .border(if (selected) 3.dp else 0.dp, if (selected) cs.onSurface else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { onChange(draft.copy(pattern = i)) }
                                    .testTag("cover-pattern-$i"),
                            ) { drawHomePattern(p, Color.White.copy(alpha = 0.55f), 14.dp.toPx()) }
                            Text(p.label.substringBefore(' '), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onSave, modifier = Modifier.testTag(TAG_COVER_SAVE)) { Text(if (draft.id == null) "Make book" else "Save") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("cover-delete")) {
                            Icon(Icons.Filled.Delete, "Delete journal", tint = cs.error)
                        }
                    }
                }
            }
        }
    }
}

/** A cloth-bound book: coloured cover, stitched pattern, a darker spine and a title label. */
@Composable
private fun BookCover(title: String, color: Int, pattern: Int, modifier: Modifier, big: Boolean = false) {
    val cloth = coverColor(color)
    val dark = lerp(cloth, Color.Black, 0.3f)
    val shape = RoundedCornerShape(topEnd = if (big) 22.dp else 12.dp, bottomEnd = if (big) 22.dp else 12.dp, topStart = 4.dp, bottomStart = 4.dp)
    Box(
        modifier
            .aspectRatio(0.72f)
            .clip(shape)
            .background(cloth)
            .drawBehind {
                drawHomePattern(COVER_PATTERNS[pattern.mod(COVER_PATTERNS.size)], Color.White.copy(alpha = 0.16f), (if (big) 34 else 20).dp.toPx())
                // Spine, with a running stitch where the cover folds.
                val spine = size.width * 0.1f
                drawRect(dark.copy(alpha = 0.85f), size = Size(spine, size.height))
                drawLine(
                    Color.White.copy(alpha = 0.5f), Offset(spine + 4.dp.toPx(), 6.dp.toPx()), Offset(spine + 4.dp.toPx(), size.height - 6.dp.toPx()),
                    1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // The title label, like a paper plate glued on the cloth.
        Box(
            Modifier
                .padding(start = if (big) 30.dp else 14.dp, end = if (big) 22.dp else 8.dp)
                .clip(RoundedCornerShape(if (big) 10.dp else 6.dp))
                .background(Color(0xFFF7F0E1))
                .border(1.dp, dark.copy(alpha = 0.4f), RoundedCornerShape(if (big) 10.dp else 6.dp))
                .padding(horizontal = if (big) 18.dp else 8.dp, vertical = if (big) 14.dp else 6.dp),
        ) {
            Text(
                title.ifBlank { "Journal" },
                style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2B2420),
                textAlign = TextAlign.Center,
                maxLines = if (big) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Cover screen: the picked book shown closed, waiting to be unfolded. */
@Composable
private fun ClosedBook(book: Journal, vm: JournalViewModel, onOpenHere: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf<CoverDraft?>(null) }
    // The book drops onto the table with a little bounce.
    val drop = remember(book.id) { Animatable(0.85f) }
    LaunchedEffect(book.id) { drop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.surfaceContainerLowest)
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(20.dp)
            .testTag(TAG_CLOSED_BOOK),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::closeBook, modifier = Modifier.testTag("journal-shelf")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the shelf")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { editing = CoverDraft(book.id, book.title, book.color, book.pattern) }, modifier = Modifier.testTag("journal-edit-cover")) {
                Icon(Icons.Filled.Edit, "Edit cover")
            }
        }
        val e = editing
        if (e != null) {
            CoverEditor(
                e,
                onChange = { editing = it },
                onSave = { vm.updateJournal(book.copy(title = e.title, color = e.color, pattern = e.pattern)); editing = null },
                onCancel = { editing = null },
                onDelete = { vm.trashJournal(book.id); editing = null },
            )
        } else {
            BookCover(
                book.title, book.color, book.pattern,
                Modifier
                    .fillMaxWidth(0.78f)
                    .widthIn(max = 360.dp)
                    .graphicsLayer { scaleX = drop.value; scaleY = drop.value; rotationZ = (1f - drop.value) * -12f },
                big = true,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.UnfoldMore, null, tint = cs.primary, modifier = Modifier.graphicsLayer { rotationZ = 90f })
            Spacer(Modifier.width(8.dp))
            Text("Unfold your phone to open", style = MaterialTheme.typography.titleMedium, color = cs.primary)
        }
        OutlinedButton(onClick = onOpenHere, modifier = Modifier.testTag(TAG_OPEN_HERE)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open here instead")
        }
    }
}

/** The open book: its dated pages beside (or, on the cover screen, instead of) the page being written. */
@Composable
private fun OpenBook(vm: JournalViewModel, book: Journal, wide: Boolean, onBack: () -> Unit) {
    val pages by vm.pages.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    LaunchedEffect(book.id) { vm.openFirstPage() }
    // The cover swings open the first time the book is shown open.
    val swing = remember(book.id) { Animatable(0f) }
    LaunchedEffect(book.id) { swing.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    var listOpen by rememberSaveable(book.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().testTag(TAG_OPEN_BOOK)) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                PageList(
                    book, pages, page?.id,
                    onOpen = vm::openPage,
                    onToday = vm::newPageToday,
                    onBack = onBack,
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                )
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val p = page
                    if (p != null) PageEditor(vm, book, p, wide = true, nav = {}, extra = {})
                    else EmptyPage(vm::newPageToday)
                }
            }
        } else if (listOpen || page == null) {
            PageList(
                book, pages, page?.id,
                onOpen = { vm.openPage(it); listOpen = false },
                onToday = { vm.newPageToday(); listOpen = false },
                onBack = { if (listOpen && page != null) listOpen = false else onBack() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BackHandler { onBack() }
            PageEditor(
                vm, book, page!!, wide = false,
                nav = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("journal-close")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close the book")
                    }
                },
                extra = {
                    TextButton(onClick = { listOpen = true }, modifier = Modifier.testTag("journal-pages")) { Text("Pages") }
                },
            )
        }
        if (swing.value < 1f) {
            // The front cover, hinged on the left, turning away to show the pages.
            BookCover(
                book.title, book.color, book.pattern,
                Modifier
                    .fillMaxHeight()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -110f * swing.value
                        cameraDistance = 16f * density
                        alpha = if (swing.value > 0.8f) (1f - swing.value) * 5f else 1f
                    },
                big = true,
            )
        }
    }
}

@Composable
private fun PageList(
    book: Journal,
    pages: List<JournalPage>,
    selectedId: Long?,
    onOpen: (Long) -> Unit,
    onToday: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val cloth = coverColor(book.color)
    Column(modifier.background(cs.surfaceContainerLow).safeDrawingPadding().testTag(TAG_PAGE_LIST)) {
        // The book's inside cover: its colour, title and the page count.
        Row(
            Modifier
                .fillMaxWidth()
                .background(cloth)
                .drawBehind { drawHomePattern(COVER_PATTERNS[book.pattern.mod(COVER_PATTERNS.size)], Color.White.copy(alpha = 0.14f), 22.dp.toPx()) }
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("journal-back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(book.title.ifBlank { "Journal" }, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (pages.size == 1) "1 page" else "${pages.size} pages", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            }
        }
        FilledTonalButton(
            onClick = onToday,
            modifier = Modifier.fillMaxWidth().padding(12.dp).testTag(TAG_TODAY_PAGE),
        ) {
            Icon(Icons.Filled.Today, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Today's page")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(pages, key = { it.id }) { p ->
                val selected = p.id == selectedId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) cs.secondaryContainer else Color.Transparent)
                        .clickable { onOpen(p.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("page-${p.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateStamp(LocalDate.ofEpochDay(p.day), cloth)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            p.title.ifBlank { LocalDate.ofEpochDay(p.day).format(DateTimeFormatter.ofPattern("EEEE")) },
                            style = MaterialTheme.typography.titleSmall,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (p.body.isNotBlank()) {
                            Text(p.body.lineSequence().first(), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

/** A little calendar-page stamp: month over day number. */
@Composable
private fun DateStamp(date: LocalDate, color: Color) {
    Column(
        Modifier
            .width(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("MMM")).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(color).padding(vertical = 1.dp),
        )
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun EmptyPage(onToday: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pick a page, or start today's", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onToday) { Text("Today's page") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageEditor(
    vm: JournalViewModel,
    book: Journal,
    page: EditorState,
    wide: Boolean,
    nav: @Composable () -> Unit,
    extra: @Composable () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) ConfirmDeleteDialog("this page", onConfirm = vm::trashOpenPage, onDismiss = { confirmDelete = false })
    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            extra()
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("page-delete")) {
                Icon(Icons.Filled.Delete, "Delete page")
            }
        }
    }
    // Wide: the toolbar is the top bar, so keep it below the status bar and camera.
    Column(Modifier.fillMaxSize().then(if (wide) Modifier.statusBarsPadding() else Modifier)) {
        if (!wide) {
            TopAppBar(
                title = { Text(book.title.ifBlank { "Journal" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = nav,
                actions = { actions() },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().imePadding()) {
            NoteEditor(
                note = page,
                onChange = vm::onDocChange,
                wide = wide,
                modifier = Modifier.fillMaxSize(),
                toolbarAtTop = wide,
                barStart = nav,
                barEnd = actions,
                header = {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                        Text(
                            vm.dayOfOpenPage()?.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")) ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextField(
                        value = page.title,
                        onValueChange = vm::onTitleChange,
                        placeholder = { Text("Title") },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag(TAG_PAGE_TITLE),
                    )
                },
            )
        }
    }
}

private fun shortDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("MMM d"))
