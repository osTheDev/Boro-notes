package com.boro.notes

import androidx.lifecycle.SavedStateHandle
import com.boro.notes.data.Stage
import com.boro.notes.sketch.Pt
import com.boro.notes.sketch.SketchCodec
import com.boro.notes.ui.DesignsViewModel
import com.boro.notes.ui.FILL_COLORS
import com.boro.notes.ui.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesignsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var designs: FakeDesignRepository
    private lateinit var notes: FakeNoteRepository
    private lateinit var vm: DesignsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        designs = FakeDesignRepository()
        notes = FakeNoteRepository()
        vm = DesignsViewModel(designs, notes, SavedStateHandle())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun square(x: Float, y: Float, size: Float): List<Pt> {
        val c = listOf(Pt(x, y), Pt(x + size, y), Pt(x + size, y + size), Pt(x, y + size), Pt(x, y))
        return c.zipWithNext().flatMap { (a, b) -> (0 until 10).map { k -> a + (b - a) * (k / 10f) } } + c.last()
    }

    @Test
    fun drawUndoRedoAndAutosave() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        val id = vm.sketch.value!!.id

        vm.addStroke(listOf(Pt(0f, 0f), Pt(3f, 0.1f), Pt(6f, 0f)))
        vm.addStroke(square(0f, 2f, 4f))
        assertEquals(2, vm.sketch.value!!.shapes.size)

        vm.undo()
        assertEquals(1, vm.sketch.value!!.shapes.size)
        assertTrue(vm.sketch.value!!.canRedo)
        vm.redo()
        assertEquals(2, vm.sketch.value!!.shapes.size)

        advanceUntilIdle()
        assertEquals(2, SketchCodec.decode(designs.get(id)!!.shapes).size)
    }

    @Test
    fun fillAndEraseByTapping() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        vm.addStroke(square(0f, 0f, 4f))

        vm.setFillColor(FILL_COLORS[2])
        vm.tap(Pt(2f, 2f), 0.5f)
        assertEquals(FILL_COLORS[2], vm.sketch.value!!.shapes[0].fill)

        vm.setTool(Tool.ERASE)
        vm.tap(Pt(2f, 2f), 0.5f) // middle of the square: nothing within reach
        assertEquals(1, vm.sketch.value!!.shapes.size)
        vm.tap(Pt(4.2f, 2f), 0.5f) // on the right edge
        assertTrue(vm.sketch.value!!.shapes.isEmpty())
    }

    @Test
    fun boxToolDrawsRectangleFromDrag() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        vm.setTool(Tool.BOX)
        // A wobbly drag only uses its start and end.
        vm.addStroke(listOf(Pt(0.1f, 0.2f), Pt(2f, 3f), Pt(1f, 1f), Pt(5.9f, 3.1f)))
        val shape = vm.sketch.value!!.shapes.single()
        assertEquals(listOf(Pt(0f, 0f), Pt(6f, 0f), Pt(6f, 3f), Pt(0f, 3f)), shape.points)
        assertTrue(shape.closed)
    }

    @Test
    fun pointsToolAddsDragsTogglesAndDeletesPoints() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        vm.setTool(Tool.BOX)
        vm.addStroke(listOf(Pt(0f, 0f), Pt(4f, 4f)))
        vm.setTool(Tool.POINTS)

        vm.tap(Pt(4.1f, 2f), 0.5f) // select the box
        assertEquals(0, vm.sketch.value!!.selectedShape)

        vm.tap(Pt(4.1f, 2f), 0.5f) // add a point on the right edge
        var shape = vm.sketch.value!!.shapes[0]
        assertEquals(5, shape.points.size)
        assertEquals(Pt(4f, 2f), shape.points[2])
        assertFalse(shape.corners[2])
        assertEquals(2, vm.sketch.value!!.selectedPoint)

        // Drag it outwards to bow the edge into a curve.
        assertEquals(2, vm.handleAt(Pt(4f, 2f), 0.5f))
        vm.dragHandle(2, Pt(5.6f, 2.2f))
        vm.dragHandle(2, Pt(6.1f, 1.9f))
        vm.endHandleDrag()
        shape = vm.sketch.value!!.shapes[0]
        assertEquals(Pt(6f, 2f), shape.points[2])

        vm.toggleSelectedCorner()
        assertTrue(vm.sketch.value!!.shapes[0].corners[2])

        vm.deleteSelectedPoint()
        assertEquals(4, vm.sketch.value!!.shapes[0].points.size)

        // One undo per action: delete, toggle, drag, add.
        repeat(4) { vm.undo() }
        assertEquals(4, vm.sketch.value!!.shapes[0].points.size)
        assertTrue(vm.sketch.value!!.shapes[0].corners.all { it })

        advanceUntilIdle()
    }

    @Test
    fun fillWorksOnAreaClosedBySeparateLines() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        vm.setTool(Tool.LINE)
        vm.addStroke(listOf(Pt(0f, 0f), Pt(4f, 0f)))
        vm.addStroke(listOf(Pt(4f, 0f), Pt(4f, 4f)))
        vm.addStroke(listOf(Pt(4f, 4f), Pt(0f, 4f)))
        vm.addStroke(listOf(Pt(0f, 4f), Pt(0f, 0f)))

        vm.setFillColor(FILL_COLORS[3])
        vm.tap(Pt(2f, 2f), 0.5f)
        val shapes = vm.sketch.value!!.shapes
        assertEquals(5, shapes.size)
        assertEquals(FILL_COLORS[3], shapes[0].fill)
        assertFalse(shapes[0].stroke)

        // Tapping again recolours the same region instead of stacking another.
        vm.setFillColor(FILL_COLORS[4])
        vm.tap(Pt(2f, 2f), 0.5f)
        assertEquals(5, vm.sketch.value!!.shapes.size)
        assertEquals(FILL_COLORS[4], vm.sketch.value!!.shapes[0].fill)

        // Clearing removes the region.
        vm.setFillColor(null)
        vm.tap(Pt(2f, 2f), 0.5f)
        assertEquals(4, vm.sketch.value!!.shapes.size)
        advanceUntilIdle()
    }

    @Test
    fun selectGroupMoveAndMirror() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        vm.setTool(Tool.BOX)
        vm.addStroke(listOf(Pt(0f, 0f), Pt(4f, 4f)))
        vm.setTool(Tool.HARDWARE)
        vm.tap(Pt(1.1f, 0.9f), 0.5f) // a rivet near the corner
        assertEquals(com.boro.notes.sketch.Stamp.RIVET, vm.sketch.value!!.shapes[1].stamp)
        assertEquals(Pt(1f, 1f), vm.sketch.value!!.shapes[1].points[0])

        vm.setTool(Tool.SELECT)
        vm.tap(Pt(2f, 2f), 0.5f) // inside the box
        vm.tap(Pt(1f, 1f), 0.5f) // the rivet
        assertEquals(setOf(0, 1), vm.sketch.value!!.selection)
        vm.groupSelection()
        vm.topstitchSelection()
        assertEquals(3, vm.sketch.value!!.shapes.size)
        assertEquals(1, vm.sketch.value!!.shapes.map { it.group }.toSet().size)

        // Tapping any piece of a group selects the whole group.
        vm.clearSelection()
        vm.tap(Pt(1f, 1f), 0.5f)
        assertEquals(setOf(0, 1, 2), vm.sketch.value!!.selection)

        // Drag the pocket 3 squares right; it moves as one piece.
        assertTrue(vm.beginDrag(Pt(2f, 2f), 0.5f))
        vm.drag(Pt(5.2f, 2.1f))
        vm.endDrag()
        assertEquals(Pt(3f, 0f), vm.sketch.value!!.shapes[0].points[0])
        assertEquals(Pt(4f, 1f), vm.sketch.value!!.shapes[1].points[0])

        vm.duplicateSelection(mirror = true)
        assertEquals(6, vm.sketch.value!!.shapes.size)
        assertEquals(setOf(3, 4, 5), vm.sketch.value!!.selection)

        vm.undo()
        assertEquals(3, vm.sketch.value!!.shapes.size)
        advanceUntilIdle()
    }

    @Test
    fun presetStartsWithAGarment() = runTest(dispatcher) {
        vm.createDesign(com.boro.notes.sketch.Preset.JEANS_FRONT)
        advanceUntilIdle()
        assertEquals("Jeans (front)", vm.sketch.value!!.name)
        assertTrue(vm.sketch.value!!.shapes.size > 10)
    }

    @Test
    fun stagesAdvanceToDone() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        val id = vm.sketch.value!!.id
        repeat(6) { vm.advanceStage(id); advanceUntilIdle() }
        assertEquals(Stage.DONE, designs.get(id)!!.stage)
        assertEquals(Stage.DONE, vm.sketch.value!!.stage)
    }

    @Test
    fun linkedNoteIsCreatedWithDesignName() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        vm.rename("Cropped trench")
        vm.createLinkedNote()
        advanceUntilIdle()

        val noteId = vm.sketch.value!!.noteId!!
        assertEquals("Cropped trench", notes.get(noteId)!!.title)
        assertEquals(noteId, designs.get(vm.sketch.value!!.id)!!.noteId)
    }

    @Test
    fun stageFilterAndDelete() = runTest(dispatcher) {
        vm.createDesign()
        advanceUntilIdle()
        val first = vm.sketch.value!!.id
        vm.setStage(Stage.PATTERN)
        vm.close()
        vm.createDesign()
        advanceUntilIdle()

        vm.setStageFilter(Stage.PATTERN)
        advanceUntilIdle()
        assertEquals(listOf(first), vm.filtered.value.map { it.id })

        vm.deleteOpenDesign()
        advanceUntilIdle()
        assertNull(vm.sketch.value)
        assertEquals(1, designs.designs.value.size)
        assertFalse(vm.all.value.isEmpty())
    }
}
