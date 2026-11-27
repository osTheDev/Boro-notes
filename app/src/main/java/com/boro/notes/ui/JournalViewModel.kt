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

    private val _bookView = MutableStateFlow(false)
    /** Inner screen: the open book spread across the whole screen as two facing pages. */
    val bookView: StateFlow<Boolean> = _bookView.asStateFlow()

    fun setBookView(on: Boolean) {
        _bookView.value = on
        if (!on) _reading.value = false
    }

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
        _openId.value = id
    }

    fun closeBook() {
        _bookView.value = false
        leavePage()
        _openId.value = null
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

    fun openPage(id: Long) {
        if (_page.value?.id == id) return
        leavePage()
        viewModelScope.launch {
            val p = repo.getPage(id) ?: return@launch
            pageDay = p.day
            _page.value = EditorState(p.id, p.title, p.body, p.content)
        }
    }

    /** Today's page: opens it if it exists, otherwise starts one. */
    fun newPageToday() {
        val journal = _openId.value ?: return
        val day = today().toEpochDay()
        viewModelScope.launch {
            val existing = repo.observePages(journal).first().firstOrNull { it.day == day }
            if (existing != null) openPage(existing.id)
            else if (_page.value?.let { pageDay == day } != true) {
                leavePage()
                startPage(journal, day)
            }
        }
    }

    /** A fresh page dated today, even if today already has one (turning to a blank page). */
    fun newPage() {
        val journal = _openId.value ?: return
        leavePage()
        viewModelScope.launch { startPage(journal, today().toEpochDay()) }
    }

    private suspend fun startPage(journal: Long, day: Long) {
        val id = repo.createPage(journal, day)
        pageDay = day
        _page.value = EditorState(id, "", "", NEW_PAGE)
    }

    fun dayOfOpenPage(): LocalDate? = pageDay?.let { LocalDate.ofEpochDay(it) }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onDocChange(content: String, plain: String) = edit { it.copy(body = plain, content = content) }

    fun trashOpenPage() {
        val p = _page.value ?: return
        saveJob?.cancel()
        _page.value = null
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
    private fun leavePage() {
        val p = _page.value ?: return
        saveJob?.cancel()
        _page.value = null
        viewModelScope.launch {
            if (p.title.isBlank() && p.body.isBlank()) repo.deletePage(p.id)
            else repo.savePage(p.id, p.title, p.body, p.content)
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
