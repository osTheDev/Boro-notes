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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
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
const val TAG_BOOK_VIEW = "bookView"
const val TAG_BOOK_SPREAD = "bookSpread"
const val TAG_BOOK_INFO = "bookInfo"
const val TAG_READ_MODE = "readMode"
const val TAG_LEFT_PAGE = "leftPage"
const val TAG_RIGHT_PAGE = "rightPage"

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
    val bookView by vm.bookView.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().testTag(TAG_OPEN_BOOK)) {
        if (wide && bookView) {
            BackHandler { vm.setBookView(false) }
            BookSpread(vm, book, pages, page, onBack = onBack)
        } else if (wide) {
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
                    if (p != null) PageEditor(vm, book, p, wide = true, nav = {}, extra = { BookViewToggle(vm, on = false) })
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

/** Switches the open book between the normal layout and the full-screen two-page spread. */
@Composable
private fun BookViewToggle(vm: JournalViewModel, on: Boolean) {
    IconButton(onClick = { vm.setBookView(!on) }, modifier = Modifier.testTag(TAG_BOOK_VIEW)) {
        Icon(if (on) Icons.Filled.CloseFullscreen else Icons.Filled.OpenInFull, if (on) "Leave book view" else "Book view")
    }
}

/**
 * The whole inner screen as an open book: the cloth cover frames two facing pages with a stitched
 * spine between. The right page is the one being written; the left page is the one before it (or
 * the contents). Swipe across the pages to flip through the book.
 */
@Composable
private fun BookSpread(vm: JournalViewModel, book: Journal, pages: List<JournalPage>, page: EditorState?, onBack: () -> Unit) {
    val cloth = coverColor(book.color)
    val spine = lerp(cloth, Color.Black, 0.3f)
    val paperColor = Color(0xFFFBF5E6)
    val ink = Color(0xFF3A322C)
    // Even corners: uneven rounded clips hit-test through Path ops, which tests can't run.
    val leftShape = RoundedCornerShape(8.dp)
    val rightShape = RoundedCornerShape(8.dp)
    val formatPinned by vm.formatPinned.collectAsStateWithLifecycle()
    val reading by vm.reading.collectAsStateWithLifecycle()
    var showContents by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) ConfirmDeleteDialog("this page", onConfirm = vm::trashOpenPage, onDismiss = { confirmDelete = false })
    val words = page?.body?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
    val index = pages.indexOfFirst { it.id == page?.id }

    // Pages oldest first, like a book; the open page sits on the right, the one before on the left.
    val ordered = pages.reversed()
    val pos = ordered.indexOfFirst { it.id == page?.id }
    val older = if (pos > 0) ordered[pos - 1] else null
    val newer = if (pos >= 0 && pos < ordered.lastIndex) ordered[pos + 1] else null
    val todayDay = vm.todayDate().toEpochDay()
    val canStartToday = ordered.lastOrNull()?.day != todayDay

    // Flip: the new right page turns in from the spine (forward) or the old one turns back.
    var flipDir by remember { mutableIntStateOf(0) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(page?.id) {
        if (flipDir != 0) {
            turn.snapTo(flipDir * 80f)
            turn.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            flipDir = 0
        }
    }
    val scope = rememberCoroutineScope()
    // A short wiggle of the page when there's nowhere to flip to.
    fun nudge(dir: Int) {
        scope.launch {
            turn.animateTo(dir * 12f, tween(120))
            turn.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }
    // A short message in the strip by the camera, e.g. why a page won't turn.
    var hint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint) { if (hint != null) { delay(2200); hint = null } }
    fun forward() {
        when {
            newer != null -> { flipDir = 1; showContents = false; vm.openPage(newer.id) }
            canStartToday -> { flipDir = 1; showContents = false; vm.newPageToday() }
            // Past the last page: a fresh blank page, like turning to an empty one. Left blank, it's dropped.
            page != null && (page.body.isNotBlank() || page.title.isNotBlank()) -> { flipDir = 1; showContents = false; vm.newPage() }
            else -> { nudge(1); hint = "Write on this page to turn to a new one" }
        }
    }
    fun back() {
        if (older != null) { flipDir = -1; showContents = false; vm.openPage(older.id) }
        else { nudge(-1); hint = "This is the first page" }
    }
    // The gesture below lives across recompositions; always call the latest versions.
    val onForward by rememberUpdatedState(::forward)
    val onBack2 by rememberUpdatedState(::back)
    val localDensity = androidx.compose.ui.platform.LocalDensity.current
    // A short drag (about a finger's width) or a quick flick turns the page.
    val swipeDistance = with(localDensity) { 48.dp.toPx() }
    val flickSpeed = with(localDensity) { 600.dp.toPx() }

    Column(
        Modifier
            .fillMaxSize()
            .background(cloth)
            .drawBehind { drawHomePattern(COVER_PATTERNS[book.pattern.mod(COVER_PATTERNS.size)], Color.White.copy(alpha = 0.12f), 26.dp.toPx()) }
            .testTag(TAG_BOOK_SPREAD),
    ) {
        // The strip by the camera, otherwise dead space: what day, how much, where in the book.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars), contentAlignment = Alignment.Center) {
            Text(
                hint ?: listOfNotNull(
                    vm.dayOfOpenPage()?.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                    if (page != null) (if (words == 1) "1 word" else "$words words") else null,
                    if (index >= 0) "page ${pages.size - index} of ${pages.size}" else null,
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.testTag(TAG_BOOK_INFO),
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
                // A sideways swipe anywhere on the pages flips them; up and down still scrolls a page.
                // Watched on the way down (Initial pass), before the text on the page can claim the
                // drag for selecting; a clearly sideways drag is then taken for the flip.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val velocity = VelocityTracker()
                        velocity.addPosition(down.uptimeMillis, down.position)
                        var dx = 0f
                        var dy = 0f
                        var flipping = false
                        val pageWidth = (size.width / 2f).coerceAtLeast(1f)
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            velocity.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) break
                            val delta = change.position - change.previousPosition
                            dx += delta.x
                            dy += delta.y
                            if (!flipping && abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy)) flipping = true
                            if (flipping) {
                                change.consume()
                                // The page leans with the finger while dragging.
                                scope.launch { turn.snapTo((-dx / pageWidth * 70f).coerceIn(-70f, 70f)) }
                            }
                        }
                        if (flipping) {
                            val vx = velocity.calculateVelocity().x
                            when {
                                dx < -swipeDistance || vx < -flickSpeed -> onForward()
                                dx > swipeDistance || vx > flickSpeed -> onBack2()
                                else -> scope.launch { turn.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                            }
                        }
                    }
                },
        ) {
            // Left page: the page before (read only), or the contents.
            Box(Modifier.weight(1f).fillMaxHeight().clip(leftShape).background(paperColor).testTag(TAG_LEFT_PAGE)) {
                if (showContents || older == null) {
                    PageList(
                        book, pages, page?.id,
                        onOpen = { id ->
                            flipDir = if ((ordered.indexOfFirst { it.id == id }) < pos) -1 else 1
                            showContents = false
                            vm.openPage(id)
                        },
                        onToday = { flipDir = 1; showContents = false; vm.newPageToday() },
                        onBack = onBack,
                        paper = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        PageHeader(ink) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("journal-back-spread")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close the book", tint = ink) }
                            Text(
                                book.title.ifBlank { "Journal" },
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Serif,
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { showContents = true }, modifier = Modifier.testTag("spread-contents")) {
                                Icon(Icons.AutoMirrored.Filled.List, "Contents", tint = ink)
                            }
                        }
                        val prev = EditorState(older.id, older.title, older.body, older.content)
                        NoteEditor(
                            note = prev,
                            onChange = { _, _ -> },
                            wide = true,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            toolbarAtTop = true,
                            showToolbar = false,
                            readOnly = true,
                            header = { PageHeading(LocalDate.ofEpochDay(older.day), older.title) },
                        )
                    }
                }
            }
            // The spine: a darker band of cloth with a running stitch down it.
            Box(
                Modifier
                    .width(16.dp)
                    .fillMaxHeight()
                    .background(spine)
                    .drawBehind {
                        drawLine(
                            Color.White.copy(alpha = 0.45f),
                            Offset(size.width / 2, 8.dp.toPx()), Offset(size.width / 2, size.height - 8.dp.toPx()),
                            1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                        )
                    },
            )
            // Right page: the page being written, turning on the spine when flipped.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        rotationY = -turn.value
                        cameraDistance = 20f * density
                    }
                    .clip(rightShape)
                    .background(paperColor)
                    .testTag(TAG_RIGHT_PAGE),
            ) {
                Column(Modifier.fillMaxSize()) {
                    PageHeader(ink) {
                        Spacer(Modifier.weight(1f))
                        SpreadToggle(Icons.Filled.TextFormat, if (formatPinned) "Hide formatting" else "Show formatting", formatPinned && !reading, ink, enabled = !reading, tag = "spread-format", onClick = vm::toggleFormat)
                        SpreadToggle(Icons.Filled.AutoStories, if (reading) "Edit" else "Read only", reading, ink, tag = TAG_READ_MODE, onClick = vm::toggleReading)
                        if (page != null && !reading) {
                            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("page-delete")) { Icon(Icons.Filled.Delete, "Delete page", tint = ink) }
                        }
                        IconButton(onClick = { vm.setBookView(false) }, modifier = Modifier.testTag(TAG_BOOK_VIEW)) { Icon(Icons.Filled.CloseFullscreen, "Leave book view", tint = ink) }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (page != null) {
                            PageEditor(
                                vm, book, page, wide = true, insetTop = false, nav = {}, extra = {},
                                showActions = false, showToolbar = formatPinned, readOnly = reading,
                            )
                        } else {
                            EmptyPage(vm::newPageToday)
                        }
                    }
                }
            }
        }
    }
}

/** A slim row of controls along the top of a page in the book view. */
@Composable
private fun PageHeader(ink: Color, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The date and title at the top of a page being read. */
@Composable
private fun PageHeading(date: LocalDate, title: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Serif,
            color = MaterialTheme.colorScheme.primary,
        )
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A round on/off control along the top of the book view's page. */
@Composable
private fun SpreadToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, on: Boolean, ink: Color, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .background(if (on) ink.copy(alpha = 0.14f) else Color.Transparent)
            .testTag(tag),
    ) { Icon(icon, description, tint = ink.copy(alpha = if (enabled) 1f else 0.35f)) }
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
    /** Drawn as a cream paper page (the left page of the book view). */
    paper: Boolean = false,
) {
    val cloth = coverColor(book.color)
    if (paper) {
        // Paper page colours, whatever the app theme.
        val ink = Color(0xFF1F1B16)
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = Color(0xFFFBF5E6), surfaceContainerLow = Color(0xFFF5EEDC), onSurface = ink,
                onSurfaceVariant = ink.copy(alpha = 0.6f), outlineVariant = ink.copy(alpha = 0.2f),
                secondaryContainer = cloth.copy(alpha = 0.16f), onSecondaryContainer = ink,
            ),
        ) { PageListContent(book, pages, selectedId, onOpen, onToday, onBack, modifier, cloth, insets = false) }
    } else {
        PageListContent(book, pages, selectedId, onOpen, onToday, onBack, modifier, cloth, insets = true)
    }
}

@Composable
private fun PageListContent(
    book: Journal,
    pages: List<JournalPage>,
    selectedId: Long?,
    onOpen: (Long) -> Unit,
    onToday: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    cloth: Color,
    insets: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.background(cs.surfaceContainerLow).then(if (insets) Modifier.safeDrawingPadding() else Modifier).testTag(TAG_PAGE_LIST)) {
        // The book's inside cover: its colour, title and the page count. In the book view it's
        // printed on the page itself.
        val headInk = if (insets) Color.White else cs.onSurface
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (insets) Modifier
                        .background(cloth)
                        .drawBehind { drawHomePattern(COVER_PATTERNS[book.pattern.mod(COVER_PATTERNS.size)], Color.White.copy(alpha = 0.14f), 22.dp.toPx()) }
                    else Modifier
                )
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("journal-back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = headInk)
            }
            Column(Modifier.weight(1f)) {
                Text(book.title.ifBlank { "Journal" }, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif, color = headInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (pages.size == 1) "1 page" else "${pages.size} pages", style = MaterialTheme.typography.labelMedium, color = headInk.copy(alpha = 0.7f))
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
    /** Pad below the status bar (off when the book view frame already does). */
    insetTop: Boolean = true,
    /** Book view: the actions live in the band above the pages instead. */
    showActions: Boolean = true,
    showToolbar: Boolean = true,
    readOnly: Boolean = false,
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
    Column(Modifier.fillMaxSize().then(if (wide && insetTop) Modifier.statusBarsPadding() else Modifier)) {
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
                barEnd = { if (showActions) actions() },
                showToolbar = showToolbar,
                readOnly = readOnly,
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
                        readOnly = readOnly,
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
