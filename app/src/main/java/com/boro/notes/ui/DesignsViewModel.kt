package com.boro.notes.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boro.notes.data.Design
import com.boro.notes.data.DesignRepository
import com.boro.notes.data.NoteRepository
import com.boro.notes.data.Stage
import com.boro.notes.sketch.DrawOptions
import com.boro.notes.sketch.Geometry
import com.boro.notes.sketch.Pt
import com.boro.notes.sketch.Shape
import com.boro.notes.sketch.SketchCodec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tool(val label: String) {
    DRAW("Draw"),
    LINE("Line"),
    BOX("Box"),
    OVAL("Oval"),
    POINTS("Points"),
    FILL("Fill"),
    ERASE("Erase"),
}

/** The design open on the canvas, with its drawing and the canvas tool settings. */
data class SketchState(
    val id: Long,
    val name: String,
    val stage: Stage,
    val noteId: Long?,
    val shapes: List<Shape>,
    val tool: Tool = Tool.DRAW,
    val options: DrawOptions = DrawOptions(),
    val fillColor: Int? = FILL_COLORS[1],
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Points tool: the shape being edited and its selected point. */
    val selectedShape: Int? = null,
    val selectedPoint: Int? = null,
)

/** Fill swatches; null clears a fill. */
val FILL_COLORS: List<Int?> = listOf(
    null,
    0xFFE57373.toInt(),
    0xFF64B5F6.toInt(),
    0xFF81C784.toInt(),
    0xFFFFD54F.toInt(),
    0xFFBA68C8.toInt(),
    0xFF90A4AE.toInt(),
)

class DesignsViewModel(
    private val designs: DesignRepository,
    private val notes: NoteRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    /** null shows every stage. */
    val stageFilter: StateFlow<Stage?> = savedState.getStateFlow<Stage?>(KEY_FILTER, null)

    val all: StateFlow<List<Design>> = designs.observeDesigns()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filtered: StateFlow<List<Design>> = combine(all, stageFilter) { list, stage ->
        if (stage == null) list else list.filter { it.stage == stage }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _sketch = MutableStateFlow<SketchState?>(null)
    val sketch: StateFlow<SketchState?> = _sketch.asStateFlow()

    private val undoStack = ArrayDeque<List<Shape>>()
    private val redoStack = ArrayDeque<List<Shape>>()
    private var saveJob: Job? = null

    init {
        savedState.get<Long>(KEY_OPEN_ID)?.let { open(it) }
    }

    fun setStageFilter(stage: Stage?) {
        savedState[KEY_FILTER] = stage
    }

    fun createDesign() {
        flush()
        viewModelScope.launch { load(designs.create()) }
    }

    fun open(id: Long) {
        if (_sketch.value?.id == id) return
        flush()
        viewModelScope.launch { load(id) }
    }

    fun close() {
        flush()
        _sketch.value = null
        undoStack.clear()
        redoStack.clear()
        savedState.remove<Long>(KEY_OPEN_ID)
    }

    fun deleteOpenDesign() {
        val id = _sketch.value?.id ?: return
        saveJob?.cancel()
        _sketch.value = null
        savedState.remove<Long>(KEY_OPEN_ID)
        viewModelScope.launch { designs.delete(id) }
    }

    fun advanceStage(id: Long) {
        viewModelScope.launch { designs.update(id) { it.copy(stage = it.stage.next()) } }
        _sketch.value?.takeIf { it.id == id }?.let { _sketch.value = it.copy(stage = it.stage.next()) }
    }

    fun setStage(stage: Stage) = updateOpen(persist = true) { it.copy(stage = stage) }

    fun rename(name: String) = updateOpen(persist = false) { it.copy(name = name) }.also { scheduleSave() }

    fun setTool(tool: Tool) = updateOpen(persist = false) {
        it.copy(tool = tool, selectedShape = null, selectedPoint = null)
    }

    fun setFillColor(color: Int?) = updateOpen(persist = false) { it.copy(fillColor = color, tool = Tool.FILL) }

    fun toggleSnap() = updateOpen(persist = false) {
        it.copy(options = it.options.copy(snapToGrid = !it.options.snapToGrid))
    }

    fun toggleStraighten() = updateOpen(persist = false) {
        it.copy(options = it.options.copy(straighten = !it.options.straighten))
    }

    /** The shape a stroke (in grid units) turns into with the current tool; also used for the live preview. */
    fun shapeFor(raw: List<Pt>): Shape? {
        val current = _sketch.value ?: return null
        if (raw.size < 2) return null
        val from = raw.first()
        val to = raw.last()
        return when (current.tool) {
            Tool.DRAW -> Geometry.recognize(raw, current.shapes, current.options)
            Tool.LINE -> Geometry.line(from, to, current.shapes, current.options)
            Tool.BOX -> Geometry.box(from, to, current.shapes, current.options)
            Tool.OVAL -> Geometry.oval(from, to, current.options)
            Tool.POINTS, Tool.FILL, Tool.ERASE -> null
        }
    }

    /** A finished finger stroke, in grid units. */
    fun addStroke(raw: List<Pt>) {
        val current = _sketch.value ?: return
        val shape = shapeFor(raw) ?: return
        commitShapes(current.shapes + shape)
    }

    /** A tap on the canvas: fills or erases depending on the tool. [tolerance] is in grid units. */
    fun tap(at: Pt, tolerance: Float) {
        val current = _sketch.value ?: return
        when (current.tool) {
            Tool.DRAW, Tool.LINE, Tool.BOX, Tool.OVAL -> Unit
            Tool.POINTS -> pointsTap(at, tolerance)
            Tool.FILL -> fillAt(at)
            Tool.ERASE -> {
                val index = Geometry.hitTarget(current.shapes, at, tolerance) ?: return
                commitShapes(current.shapes.filterIndexed { i, _ -> i != index })
            }
        }
    }

    // --- Points tool ---

    private var dragging = false

    private fun selected(): Shape? = _sketch.value?.let { s -> s.selectedShape?.let { s.shapes.getOrNull(it) } }

    /** The point of the selected shape under the finger, if any; [tolerance] is in grid units. */
    fun handleAt(at: Pt, tolerance: Float): Int? = selected()?.let { Geometry.pointAt(it, at, tolerance) }

    /** Moves a point of the selected shape while the finger drags it. */
    fun dragHandle(index: Int, to: Pt) {
        val current = _sketch.value ?: return
        val shapeIndex = current.selectedShape ?: return
        val shape = current.shapes.getOrNull(shapeIndex) ?: return
        if (index !in shape.points.indices) return
        if (!dragging) {
            dragging = true
            undoStack.addLast(current.shapes)
            redoStack.clear()
        }
        val target = if (current.options.snapToGrid) Geometry.snap(to) else to
        val moved = shape.copy(points = shape.points.toMutableList().also { it[index] = target })
        _sketch.value = current.copy(
            shapes = current.shapes.toMutableList().also { it[shapeIndex] = moved },
            selectedPoint = index,
            canUndo = true,
            canRedo = false,
        )
    }

    fun endHandleDrag() {
        dragging = false
        scheduleSave()
    }

    /** Points tool tap: pick a point, add a point on the selected line, or select another line. */
    private fun pointsTap(at: Pt, tolerance: Float) {
        val current = _sketch.value ?: return
        val shape = selected()
        if (shape != null) {
            Geometry.pointAt(shape, at, tolerance)?.let { index ->
                _sketch.value = current.copy(selectedPoint = index)
                return
            }
            if (Geometry.distance(shape, at) <= tolerance) {
                val insertAt = Geometry.nearestSegment(shape, at) + 1
                val point = if (current.options.snapToGrid) Geometry.snap(at) else at
                val updated = shape.copy(
                    points = shape.points.toMutableList().also { it.add(insertAt, point) },
                    corners = shape.corners.toMutableList().also { it.add(insertAt, false) },
                )
                commitShapes(current.shapes.toMutableList().also { it[current.selectedShape!!] = updated })
                _sketch.value = _sketch.value?.copy(selectedPoint = insertAt)
                return
            }
        }
        val hit = Geometry.hitTarget(current.shapes, at, tolerance)?.takeIf { current.shapes[it].stroke }
        _sketch.value = current.copy(selectedShape = hit, selectedPoint = null)
    }

    fun deleteSelectedPoint() {
        val current = _sketch.value ?: return
        val shapeIndex = current.selectedShape ?: return
        val index = current.selectedPoint ?: return
        val shape = current.shapes.getOrNull(shapeIndex) ?: return
        if (shape.points.size <= if (shape.closed) 3 else 2) return
        val updated = shape.copy(
            points = shape.points.filterIndexed { i, _ -> i != index },
            corners = shape.corners.filterIndexed { i, _ -> i != index },
        )
        commitShapes(current.shapes.toMutableList().also { it[shapeIndex] = updated })
        _sketch.value = _sketch.value?.copy(selectedPoint = null)
    }

    /** Switches the selected point between a sharp corner and a smooth curve. */
    fun toggleSelectedCorner() {
        val current = _sketch.value ?: return
        val shapeIndex = current.selectedShape ?: return
        val index = current.selectedPoint ?: return
        val shape = current.shapes.getOrNull(shapeIndex) ?: return
        val updated = shape.copy(corners = shape.corners.toMutableList().also { it[index] = !it[index] })
        commitShapes(current.shapes.toMutableList().also { it[shapeIndex] = updated })
    }

    /**
     * Fills the innermost area around [at]: a closed shape, or an area closed off by separate
     * lines meeting end to end (stored as a fill-only region under the lines).
     */
    private fun fillAt(at: Pt) {
        val current = _sketch.value ?: return
        val shapes = current.shapes
        val index = Geometry.fillTarget(shapes, at)
        val region = Geometry.enclosedRegion(shapes, at)
        val regionWins = region != null &&
            (index == null || Geometry.polygonArea(region) < Geometry.area(shapes[index]))
        when {
            regionWins -> {
                val color = current.fillColor ?: return
                val fillShape = Shape(region!!, List(region.size) { true }, closed = true, fill = color, stroke = false)
                commitShapes(listOf(fillShape) + shapes)
            }
            index != null -> {
                val target = shapes[index]
                if (target.fill == current.fillColor) return
                commitShapes(
                    if (!target.stroke && current.fillColor == null) {
                        shapes.filterIndexed { i, _ -> i != index }
                    } else {
                        shapes.toMutableList().also { it[index] = target.copy(fill = current.fillColor) }
                    }
                )
            }
        }
    }

    fun undo() {
        val current = _sketch.value ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current.shapes)
        setShapes(previous)
        clearSelection()
    }

    fun redo() {
        val current = _sketch.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current.shapes)
        setShapes(next)
        clearSelection()
    }

    fun clearCanvas() {
        val current = _sketch.value ?: return
        if (current.shapes.isNotEmpty()) commitShapes(emptyList())
    }

    /** Creates a note named after the open design and links it. */
    fun createLinkedNote() {
        val current = _sketch.value ?: return
        viewModelScope.launch {
            val noteId = notes.create()
            notes.save(noteId, current.name.ifBlank { "Design notes" }, "")
            designs.update(current.id) { it.copy(noteId = noteId) }
            _sketch.value = _sketch.value?.takeIf { it.id == current.id }?.copy(noteId = noteId)
        }
    }

    fun unlinkNote() = updateOpen(persist = true) { it.copy(noteId = null) }

    /** Writes the open design now (on close, or the app going to the background). */
    fun flush() {
        val current = _sketch.value ?: return
        saveJob?.cancel()
        saveJob = null
        viewModelScope.launch { persist(current) }
    }

    private suspend fun load(id: Long) {
        val design = designs.get(id) ?: return
        undoStack.clear()
        redoStack.clear()
        _sketch.value = SketchState(
            id = design.id,
            name = design.name,
            stage = design.stage,
            noteId = design.noteId,
            shapes = SketchCodec.decode(design.shapes),
        )
        savedState[KEY_OPEN_ID] = design.id
    }

    private fun clearSelection() {
        _sketch.value = _sketch.value?.copy(selectedShape = null, selectedPoint = null)
    }

    private fun commitShapes(shapes: List<Shape>) {
        val current = _sketch.value ?: return
        undoStack.addLast(current.shapes)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        setShapes(shapes)
    }

    private fun setShapes(shapes: List<Shape>) {
        val current = _sketch.value ?: return
        _sketch.value = current.copy(
            shapes = shapes,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
        scheduleSave()
    }

    private fun updateOpen(persist: Boolean, change: (SketchState) -> SketchState) {
        val current = _sketch.value ?: return
        val updated = change(current)
        _sketch.value = updated
        if (persist) viewModelScope.launch { persist(updated) }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            _sketch.value?.let { persist(it) }
        }
    }

    private suspend fun persist(state: SketchState) {
        val encoded = SketchCodec.encode(state.shapes)
        designs.update(state.id) {
            it.copy(name = state.name, stage = state.stage, noteId = state.noteId, shapes = encoded)
        }
    }

    companion object {
        const val AUTOSAVE_DELAY_MS = 400L
        private const val MAX_UNDO = 100
        private const val KEY_OPEN_ID = "designOpenId"
        private const val KEY_FILTER = "designStageFilter"

        fun factory(designs: DesignRepository, notes: NoteRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DesignsViewModel(designs, notes, createSavedStateHandle()) }
            }
    }
}
