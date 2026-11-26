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
