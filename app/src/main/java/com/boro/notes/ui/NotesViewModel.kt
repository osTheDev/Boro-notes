package com.boro.notes.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boro.notes.data.Note
import com.boro.notes.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The note currently open in the editor, including edits not yet written to the database. */
data class EditorState(val id: Long, val title: String, val body: String)

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val repository: NoteRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")

    val notes: StateFlow<List<Note>> = query
        .flatMapLatest { repository.observeNotes(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private var saveJob: Job? = null

    private val _fullScreen = MutableStateFlow(false)
    /** The open note fills the whole screen (side menu and list hidden). */
    val fullScreen: StateFlow<Boolean> = _fullScreen.asStateFlow()

    fun setFullScreen(on: Boolean) {
        _fullScreen.value = on && _editor.value != null
    }

    init {
        // Restore the open note (and any unsaved draft) after process death.
        savedState.get<Long>(KEY_OPEN_ID)?.let { id ->
            viewModelScope.launch {
                val note = repository.get(id) ?: return@launch clearOpen()
                _editor.value = EditorState(
                    id = id,
                    title = savedState.get<String>(KEY_DRAFT_TITLE) ?: note.title,
                    body = savedState.get<String>(KEY_DRAFT_BODY) ?: note.body,
                )
            }
        }
    }

    fun onQueryChange(value: String) {
        savedState[KEY_QUERY] = value
    }

    fun open(id: Long) {
        if (_editor.value?.id == id) return
        flush()
        viewModelScope.launch {
            val note = repository.get(id) ?: return@launch
            setEditor(EditorState(note.id, note.title, note.body))
        }
    }

    fun createNote() {
        flush()
        viewModelScope.launch {
            val id = repository.create()
            setEditor(EditorState(id, "", ""))
        }
    }

    fun close() {
        flush()
        _fullScreen.value = false
        clearOpen()
    }

    fun deleteOpenNote() {
        val current = _editor.value ?: return
        saveJob?.cancel()
        _fullScreen.value = false
        clearOpen()
        viewModelScope.launch { repository.delete(current.id) }
    }

    fun onTitleChange(value: String) = edit { it.copy(title = value) }

    fun onBodyChange(value: String) = edit { it.copy(body = value) }

    /** Writes pending edits immediately (on close, switching notes, or the app going to the background). */
    fun flush() {
        val current = _editor.value ?: return
        saveJob?.cancel()
        saveJob = null
        viewModelScope.launch { repository.save(current.id, current.title, current.body) }
    }

    private fun edit(transform: (EditorState) -> EditorState) {
        val updated = _editor.value?.let(transform) ?: return
        _editor.value = updated
        savedState[KEY_DRAFT_TITLE] = updated.title
        savedState[KEY_DRAFT_BODY] = updated.body
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            repository.save(updated.id, updated.title, updated.body)
        }
    }

    private fun setEditor(state: EditorState) {
        _editor.value = state
        savedState[KEY_OPEN_ID] = state.id
        savedState[KEY_DRAFT_TITLE] = state.title
        savedState[KEY_DRAFT_BODY] = state.body
    }

    private fun clearOpen() {
        _editor.value = null
        savedState.remove<Long>(KEY_OPEN_ID)
        savedState.remove<String>(KEY_DRAFT_TITLE)
        savedState.remove<String>(KEY_DRAFT_BODY)
    }

    companion object {
        const val AUTOSAVE_DELAY_MS = 500L
        private const val KEY_QUERY = "query"
        private const val KEY_OPEN_ID = "openId"
        private const val KEY_DRAFT_TITLE = "draftTitle"
        private const val KEY_DRAFT_BODY = "draftBody"

        fun factory(repository: NoteRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { NotesViewModel(repository, createSavedStateHandle()) }
        }
    }
}
