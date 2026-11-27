package com.boro.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boro.notes.data.Journal
import com.boro.notes.data.JournalPage
import com.boro.notes.data.JournalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import com.boro.notes.notes.NoteDoc
import com.boro.notes.notes.Paper
import com.boro.notes.notes.PaperColor
import com.boro.notes.notes.PaperPattern
import com.boro.notes.notes.TextBlock
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** Journals: a shelf of books, each with dated pages written like notes. */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(
    private val repo: JournalRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val journals: StateFlow<List<Journal>> = repo.observeJournals()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _openId = MutableStateFlow<Long?>(null)
    /** The book picked on the shelf (shown closed on the cover screen, open when unfolded). */
    val openId: StateFlow<Long?> = _openId.asStateFlow()

    val pages: StateFlow<List<JournalPage>> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observePages(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _bookView = MutableStateFlow(true)
    /**
     * Inner screen: the open book spread across the whole screen as two facing pages. On by
     * default, so an unfolded book opens straight into it; the last choice is kept between books.
     */
    val bookView: StateFlow<Boolean> = _bookView.asStateFlow()

    fun setBookView(on: Boolean) {
        if (_bookView.value == on) return
        _bookView.value = on
        if (on) {
            // The spread opens where the page was; both pages are then written side by side.
            val at = _page.value?.let { open -> bookOrder().indexOfFirst { it.id == open.id } } ?: -1
            leavePage()
            _spread.value = if (at >= 0) at / 2 * 2 else -1
        } else {
            _reading.value = false
            val left = spreadPages().firstOrNull()
            dropTrailingBlanks(keepFrom = null)
            if (left != null && !isBlank(stateOf(left))) openPage(left.id)
        }
    }

    // ---- Book view: two facing pages, both written in place ----

    private val _spread = MutableStateFlow(-1)
    /** Book view: where the left page sits in [bookOrder] (always even); -1 means the latest spread. */
    val spread: StateFlow<Int> = _spread.asStateFlow()

    private val _drafts = MutableStateFlow<Map<Long, EditorState>>(emptyMap())
    /** Pages written in this book with edits newer than what [pages] has loaded. */
    val drafts: StateFlow<Map<Long, EditorState>> = _drafts.asStateFlow()
    private val saveJobs = mutableMapOf<Long, Job>()
    private val days = mutableMapOf<Long, Long>()
    /** Pages sent to the Trash: never dropped as blank, even before storage catches up. */
    private val trashed = mutableSetOf<Long>()

    /** Pages in reading order: oldest first, like a book. */
    fun bookOrder(list: List<JournalPage> = pages.value): List<JournalPage> = list.reversed()

    /** The spread holding the last page (where a book opens), 0 for an empty book. */
    fun latestSpread(count: Int = pages.value.size): Int = if (count == 0) 0 else (count - 1) / 2 * 2

    /** The furthest spread: the first one past the last page (its pages are made as it's turned to). */
    fun lastSpread(count: Int = pages.value.size): Int = (count + 1) / 2 * 2

    fun spreadStart(count: Int = pages.value.size): Int =
        _spread.value.let { if (it < 0) latestSpread(count) else it.coerceAtMost(lastSpread(count)) }

    /** The (up to two) pages on the spread now showing. */
    fun spreadPages(list: List<JournalPage> = pages.value): List<JournalPage> =
        bookOrder(list).drop(spreadStart(list.size)).take(2)

    /** What a page shows: its unsaved edits if any, else what's stored. */
    fun stateOf(p: JournalPage): EditorState =
        _page.value?.takeIf { it.id == p.id } ?: _drafts.value[p.id] ?: EditorState(p.id, p.title, p.body, p.content)

    /** Turns to the spread holding book page [index] (0 = first). Blank pages left at the end are dropped. */
    fun turnTo(index: Int, count: Int = pages.value.size) {
        val target = (index.coerceIn(0, lastSpread(count)) / 2) * 2
        if (target == spreadStart(count)) return
        dropTrailingBlanks(keepFrom = target)
        _spread.value = target
        ensureSpread()
    }

    /**
     * Turns the page: on through the book, and past the end onto two fresh pages, endlessly, as long
     * as something is written on the pages showing. False when there's nothing to turn to.
     */
    fun turnForward(): Boolean {
        val start = spreadStart()
        val beyond = start + 2 < pages.value.size
        if (!beyond && spreadPages().none { !isBlank(stateOf(it)) }) return false
        turnTo(start + 2)
        return true
    }

    fun turnBack(): Boolean {
        val start = spreadStart()
        if (start == 0) return false
        turnTo(start - 2)
        return true
    }

    /** Page changes run one at a time, so dropping and making pages never race each other. */
    private val pageOps = Mutex()

    /** Makes sure both pages of the showing spread exist, so they can be written on straight away. */
    fun ensureSpread() {
        val journal = _openId.value ?: return
        if (!_bookView.value || _reading.value) return
        viewModelScope.launch {
            pageOps.withLock {
                if (_openId.value != journal) return@withLock
                val n = repo.observePages(journal).first().size
                repeat((spreadStart(n) + 2 - n).coerceAtLeast(0)) {
                    val day = today().toEpochDay()
                    days[repo.createPage(journal, day)] = day
                }
            }
        }
    }

    fun spreadTitle(p: JournalPage, title: String) = editSpreadPage(p) { it.copy(title = title) }

    fun spreadDoc(p: JournalPage, content: String, plain: String) = editSpreadPage(p) { it.copy(body = plain, content = content) }

    private fun editSpreadPage(p: JournalPage, change: (EditorState) -> EditorState) {
        if (_page.value?.id == p.id) { edit(change); return }
        val updated = change(stateOf(p))
        _drafts.value = _drafts.value + (p.id to updated)
        saveJobs[p.id]?.cancel()
        saveJobs[p.id] = viewModelScope.launch {
            delay(600)
            repo.savePage(updated.id, updated.title, updated.body, updated.content)
        }
    }

    /**
     * The next fresh page at the end of the book, optionally starting a chapter there; the book turns
     * to it. A blank page already waiting at the end is used rather than adding another.
     */
    fun addPage(chapter: String? = null) {
        val journal = _openId.value ?: return
        val name = chapter?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            val id = pageOps.withLock {
                val order = bookOrder(repo.observePages(journal).first())
                val waiting = order.takeLastWhile { it.id !in trashed && isBlank(stateOf(it)) }.firstOrNull()
                val id = waiting?.id ?: today().toEpochDay().let { day -> repo.createPage(journal, day).also { days[it] = day } }
                if (name != null) repo.setChapter(id, name)
                id
            }
            val all = repo.observePages(journal).first()
            if (_openId.value != journal) return@launch
            val at = bookOrder(all).indexOfFirst { it.id == id }
            if (at >= 0) turnTo(at, all.size)
            ensureSpread()
        }
    }

    /** Starts a chapter at this page, renames it, or (blank) ends it so its pages join the chapter before. */
    fun setChapter(pageId: Long, name: String?) = viewModelScope.launch {
        repo.setChapter(pageId, name?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun trashSpreadPage(id: Long) {
        trashed += id
        saveJobs.remove(id)?.cancel()
        val state = _drafts.value[id]
        _drafts.value = _drafts.value - id
        if (_page.value?.id == id) { trashOpenPage(); return }
        viewModelScope.launch {
            state?.let { repo.savePage(it.id, it.title, it.body, it.content) }
            repo.trashPage(id)
        }
    }

    /**
     * Drops the blank pages left at the end of the book (fresh pages turned past without writing),
     * stopping at the spread from [keepFrom] on. Blank pages between written ones stay, like in a book.
     */
    private fun dropTrailingBlanks(keepFrom: Int?) {
        val order = bookOrder()
        val drop = mutableListOf<Long>()
        for (i in order.indices.reversed()) {
            val p = order[i]
            if (keepFrom != null && i <= keepFrom + 1) break
            if (_page.value?.id == p.id || p.id in trashed || !isBlank(stateOf(p))) break
            drop += p.id
        }
        if (drop.isEmpty()) return
        drop.forEach { saveJobs.remove(it)?.cancel() }
        _drafts.value = _drafts.value - drop.toSet()
        viewModelScope.launch { pageOps.withLock { drop.forEach { repo.deletePage(it) } } }
    }

    /** Nothing written: no title, no text, and the paper untouched (a drawing or photo counts as written). */
    private fun isBlank(p: EditorState) =
        p.title.isBlank() && p.body.isBlank() &&
            NoteDoc.load(p.content, p.body).blocks.none { it is com.boro.notes.notes.ImageBlock || (it is com.boro.notes.notes.DrawingBlock && it.strokes.isNotEmpty()) }

    private val _formatPinned = MutableStateFlow(false)
    /** Book view: the formatting bar stays shown (pinned) or tucked away for a cleaner page. */
    val formatPinned: StateFlow<Boolean> = _formatPinned.asStateFlow()
    fun toggleFormat() { _formatPinned.value = !_formatPinned.value }

    private val _reading = MutableStateFlow(false)
    /** Book view: read the pages without editing them. */
    val reading: StateFlow<Boolean> = _reading.asStateFlow()
    fun toggleReading() { _reading.value = !_reading.value }

    private val _page = MutableStateFlow<EditorState?>(null)
    /** The page being written, with edits not yet saved. */
    val page: StateFlow<EditorState?> = _page.asStateFlow()
    private var pageDay: Long? = null
    private var saveJob: Job? = null

    fun todayDate(): LocalDate = today()

    fun createJournal(title: String, color: Int, pattern: Int) = viewModelScope.launch {
        val id = repo.createJournal(title.trim().ifBlank { "Journal" }, color, pattern)
        _openId.value = id
    }

    fun updateJournal(journal: Journal) = viewModelScope.launch {
        repo.updateJournal(journal.copy(title = journal.title.trim().ifBlank { "Journal" }))
    }

    fun trashJournal(id: Long) = viewModelScope.launch {
        if (_openId.value == id) closeBook()
        repo.trashJournal(id)
    }

    /** Picks a book on the shelf. */
    fun select(id: Long) {
        if (_openId.value == id) return
        leavePage()
        clearBook()
        _openId.value = id
    }

    fun closeBook() {
        _reading.value = false
        if (_bookView.value) dropTrailingBlanks(keepFrom = null)
        leavePage()
        clearBook()
        _openId.value = null
    }

    private fun clearBook() {
        _drafts.value = emptyMap()
        days.clear()
        _spread.value = -1
    }

    /** Opens the newest page, or today's new page if the book is empty. */
    fun openFirstPage() {
        if (_page.value != null) return
        val journal = _openId.value ?: return
        viewModelScope.launch {
            // Read the pages from storage: the list on screen may not have loaded yet.
            val first = repo.observePages(journal).first().firstOrNull()
            if (_page.value != null || _openId.value != journal) return@launch
            if (first != null) openPage(first.id) else startPage(journal, today().toEpochDay())
        }
    }

    /**
     * Swaps straight to the page (from what's already loaded when possible), so a page turn never
     * passes through an empty moment. The page being left is saved, or dropped if blank, behind it.
     */
    fun openPage(id: Long) {
        if (_page.value?.id == id) return
        _drafts.value[id]?.let { state ->
            if (isBlank(state)) return // dropped when it was left
            show(state, days[id] ?: pages.value.firstOrNull { it.id == id }?.day)
            return
        }
        pages.value.firstOrNull { it.id == id }?.let { p ->
            show(EditorState(p.id, p.title, p.body, p.content), p.day)
            return
        }
        viewModelScope.launch {
            val p = repo.getPage(id) ?: return@launch
            show(EditorState(p.id, p.title, p.body, p.content), p.day)
        }
    }

    /** Today's page: opens it if it exists, otherwise starts one. */
    fun newPageToday() {
        val journal = _openId.value ?: return
        val day = today().toEpochDay()
        viewModelScope.launch {
            val existing = repo.observePages(journal).first().firstOrNull { it.day == day }
            if (existing != null) openPage(existing.id)
            else if (_page.value?.let { pageDay == day } != true) startPage(journal, day)
        }
    }

    /** A fresh page dated today, even if today already has one (turning to a blank page). */
    fun newPage() {
        val journal = _openId.value ?: return
        // The current page stays up until the new one exists, then they swap in one step.
        viewModelScope.launch { startPage(journal, today().toEpochDay()) }
    }

    private suspend fun startPage(journal: Long, day: Long) {
        val id = repo.createPage(journal, day)
        if (_openId.value != journal) return
        show(EditorState(id, "", "", NEW_PAGE), day)
    }

    fun dayOfOpenPage(): LocalDate? = pageDay?.let { LocalDate.ofEpochDay(it) }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onDocChange(content: String, plain: String) = edit { it.copy(body = plain, content = content) }

    fun trashOpenPage() {
        val p = _page.value ?: return
        saveJob?.cancel()
        _page.value = null
        trashed += p.id
        _drafts.value = _drafts.value - p.id
        viewModelScope.launch {
            repo.savePage(p.id, p.title, p.body, p.content)
            repo.trashPage(p.id)
        }
    }

    /** Saves the open page now, e.g. when the app goes to the background. */
    fun flush() {
        val p = _page.value ?: return
        saveJob?.cancel()
        viewModelScope.launch { repo.savePage(p.id, p.title, p.body, p.content) }
    }

    /** Saves the open page, or removes it if it was left blank. */
    private fun leavePage() = show(null, null)

    /** Shows [next] in one step, then saves (or drops, if blank) the page that was showing. */
    private fun show(next: EditorState?, day: Long?) {
        val old = _page.value
        val oldDay = pageDay
        saveJob?.cancel()
        pageDay = day
        _page.value = next
        if (day != null && next != null) days[next.id] = day
        if (old == null || old.id == next?.id) return
        _drafts.value = _drafts.value + (old.id to old)
        if (oldDay != null) days[old.id] = oldDay
        viewModelScope.launch {
            if (isBlank(old)) repo.deletePage(old.id)
            else repo.savePage(old.id, old.title, old.body, old.content)
        }
    }

    private fun edit(change: (EditorState) -> EditorState) {
        val updated = _page.value?.let(change) ?: return
        _page.value = updated
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            repo.savePage(updated.id, updated.title, updated.body, updated.content)
        }
    }

    companion object {
        /** New pages start on cream ruled paper with a margin, like a notebook. */
        private val NEW_PAGE = NoteDoc(listOf(TextBlock(NoteDoc.newId())), Paper(PaperColor.CREAM, PaperPattern.RULED, margin = true)).encode()

        fun factory(repo: JournalRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { JournalViewModel(repo) }
        }
    }
}
