package com.boro.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boro.notes.data.Idea
import com.boro.notes.data.IdeaKind
import com.boro.notes.data.IdeaRepository
import com.boro.notes.studio.Collage
import com.boro.notes.studio.CollageItem
import com.boro.notes.studio.SketchPad
import com.boro.notes.studio.StudioJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The idea open in the sketch pad or collage editor, with its decoded content. */
data class IdeaDraft(
    val idea: Idea,
    val pad: SketchPad = SketchPad(),
    val collage: Collage = Collage(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val isBlank: Boolean
        get() = idea.title.isBlank() && idea.note.isBlank() &&
            (if (idea.kind == IdeaKind.SKETCH) pad.isEmpty else collage.isEmpty)
}

/** The Sketch board: loose sketches and collages, grouped into named collections. */
class IdeasViewModel(private val repo: IdeaRepository) : ViewModel() {
    val ideas: StateFlow<List<Idea>> = repo.observeIdeas().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** null shows everything. */
    private val _collection = MutableStateFlow<String?>(null)
    val collection: StateFlow<String?> = _collection.asStateFlow()

    val collections: StateFlow<List<String>> = ideas.map { list ->
        list.mapNotNull { it.collection?.takeIf(String::isNotBlank) }.distinct().sortedBy { it.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shown: StateFlow<List<Idea>> = combine(ideas, _collection) { list, c ->
        if (c == null) list else list.filter { it.collection == c }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _open = MutableStateFlow<IdeaDraft?>(null)
    val open: StateFlow<IdeaDraft?> = _open.asStateFlow()

    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var saveJob: Job? = null

    fun showCollection(name: String?) { _collection.value = name }

    /** Starts a sketch (optionally traced over [photo]) or a collage, in the collection being shown. */
    fun create(kind: IdeaKind, photo: String? = null) {
        leaveCurrent()
        val content = when (kind) {
            IdeaKind.SKETCH -> StudioJson.encode(SketchPad(photo = photo))
            IdeaKind.COLLAGE -> StudioJson.encode(Collage())
        }
        val idea = Idea(kind = kind, content = content, collection = _collection.value)
        viewModelScope.launch {
            val id = repo.create(idea)
            load(id)
        }
    }

    fun openIdea(id: Long) {
        if (_open.value?.idea?.id == id) return
        leaveCurrent()
        viewModelScope.launch { load(id) }
    }

    fun close() {
        leaveCurrent()
        _open.value = null
    }

    fun rename(title: String) = edit { it.copy(idea = it.idea.copy(title = title)) }
    fun setNote(note: String) = edit { it.copy(idea = it.idea.copy(note = note)) }
    fun setCollection(name: String?) = edit { it.copy(idea = it.idea.copy(collection = name?.trim()?.takeIf(String::isNotEmpty))) }

    /** A change to the sketch that can be undone (a stroke, a fill, the photo). [record] false for live drags after [checkpoint]. */
    fun changePad(record: Boolean = true, change: (SketchPad) -> SketchPad) {
        val d = _open.value ?: return
        val next = change(d.pad)
        if (next == d.pad) return
        if (record) pushUndo(StudioJson.encode(d.pad))
        _open.value = d.copy(pad = next).withHistory()
        scheduleSave()
    }

    /** A change to the collage that can be undone. [record] false for live drags (recorded once at the start). */
    fun changeCollage(record: Boolean = true, change: (Collage) -> Collage) {
        val d = _open.value ?: return
        val next = change(d.collage)
        if (next == d.collage) return
        if (record) pushUndo(StudioJson.encode(d.collage))
        _open.value = d.copy(collage = next).withHistory()
        scheduleSave()
    }

    /** Marks an undo point before a live drag of collage items. */
    fun checkpoint() {
        val d = _open.value ?: return
        pushUndo(if (d.idea.kind == IdeaKind.SKETCH) StudioJson.encode(d.pad) else StudioJson.encode(d.collage))
        _open.value = d.withHistory()
    }

    fun updateItem(item: CollageItem, record: Boolean = false) =
        changeCollage(record) { c -> c.copy(items = c.items.map { if (it.id == item.id) item else it }) }

    fun undo() = step(undo, redo)
    fun redo() = step(redo, undo)

    /** Moves the open idea to the Trash. */
    fun deleteOpen() {
        val d = _open.value ?: return
        saveJob?.cancel()
        _open.value = null
        viewModelScope.launch {
            persist(d)
            repo.trash(d.idea.id)
        }
    }

    fun flush() {
        val d = _open.value ?: return
        saveJob?.cancel()
        viewModelScope.launch { persist(d) }
    }

    private fun step(from: ArrayDeque<String>, to: ArrayDeque<String>) {
        val d = _open.value ?: return
        val state = from.removeLastOrNull() ?: return
        val sketch = d.idea.kind == IdeaKind.SKETCH
        to.addLast(if (sketch) StudioJson.encode(d.pad) else StudioJson.encode(d.collage))
        _open.value = (if (sketch) d.copy(pad = StudioJson.pad(state)) else d.copy(collage = StudioJson.collage(state))).withHistory()
        scheduleSave()
    }

    private fun pushUndo(state: String) {
        if (undo.lastOrNull() == state) return
        undo.addLast(state)
        if (undo.size > 60) undo.removeFirst()
        redo.clear()
    }

    private fun IdeaDraft.withHistory() = copy(canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty())

    private fun edit(change: (IdeaDraft) -> IdeaDraft) {
        val d = _open.value ?: return
        _open.value = change(d)
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            _open.value?.let { persist(it) }
        }
    }

    private suspend fun persist(d: IdeaDraft) {
        val content = if (d.idea.kind == IdeaKind.SKETCH) StudioJson.encode(d.pad) else StudioJson.encode(d.collage)
        repo.update(d.idea.id) { it.copy(title = d.idea.title, note = d.idea.note, collection = d.idea.collection, content = content) }
    }

    /** Saves the open idea, or deletes it if nothing was added. */
    private fun leaveCurrent() {
        val d = _open.value ?: return
        saveJob?.cancel()
        undo.clear()
        redo.clear()
        viewModelScope.launch { if (d.isBlank) repo.delete(d.idea.id) else persist(d) }
    }

    private suspend fun load(id: Long) {
        val idea = repo.get(id) ?: return
        undo.clear()
        redo.clear()
        _open.value = IdeaDraft(
            idea = idea,
            pad = if (idea.kind == IdeaKind.SKETCH) StudioJson.pad(idea.content) else SketchPad(),
            collage = if (idea.kind == IdeaKind.COLLAGE) StudioJson.collage(idea.content) else Collage(),
        )
    }

    companion object {
        fun factory(repo: IdeaRepository): ViewModelProvider.Factory = viewModelFactory { initializer { IdeasViewModel(repo) } }
    }
}
