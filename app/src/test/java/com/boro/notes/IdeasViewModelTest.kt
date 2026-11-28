package com.boro.notes

import com.boro.notes.data.IdeaKind
import com.boro.notes.sketch.Pt
import com.boro.notes.studio.CollageItem
import com.boro.notes.studio.CollageKind
import com.boro.notes.studio.Pad
import com.boro.notes.studio.PadShape
import com.boro.notes.studio.StudioJson
import com.boro.notes.ui.IdeasViewModel
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
class IdeasViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeIdeaRepository
    private lateinit var vm: IdeasViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeIdeaRepository()
        vm = IdeasViewModel(repo)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private val box = Pad.shape(PadShape.BOX, Pt(0.1f, 0.1f), Pt(0.5f, 0.5f), 0xFF000000, 4f)!!

    @Test
    fun blankSketchIsNotKept() = runTest(dispatcher) {
        vm.create(IdeaKind.SKETCH)
        advanceUntilIdle()
        assertEquals(1, repo.ideas.value.size)
        vm.close()
        advanceUntilIdle()
        assertTrue(repo.ideas.value.isEmpty())
        assertNull(vm.open.value)
    }

    @Test
    fun strokesSaveAndUndo() = runTest(dispatcher) {
        vm.create(IdeaKind.SKETCH)
        advanceUntilIdle()
        vm.changePad { it.copy(strokes = it.strokes + box) }
        vm.changePad { p -> p.copy(strokes = p.strokes.map { it.copy(fill = 2) }) }
        assertTrue(vm.open.value!!.canUndo)
        vm.undo()
        assertNull(vm.open.value!!.pad.strokes.single().fill)
        vm.redo()
        assertEquals(2, vm.open.value!!.pad.strokes.single().fill)
        vm.rename("Scoop pocket")
        vm.close()
        advanceUntilIdle()
        val saved = repo.ideas.value.single()
        assertEquals("Scoop pocket", saved.title)
        assertEquals(2, StudioJson.pad(saved.content).strokes.single().fill)
    }

    @Test
    fun photoTraceStartsWithThePhoto() = runTest(dispatcher) {
        vm.create(IdeaKind.SKETCH, photo = "pocket.jpg")
        advanceUntilIdle()
        assertEquals("pocket.jpg", vm.open.value!!.pad.photo)
        vm.close()
        advanceUntilIdle()
        // A photo alone is worth keeping.
        assertEquals("pocket.jpg", StudioJson.pad(repo.ideas.value.single().content).photo)
    }

    @Test
    fun collectionsGroupAndFilter() = runTest(dispatcher) {
        vm.create(IdeaKind.SKETCH)
        advanceUntilIdle()
        vm.changePad { it.copy(strokes = listOf(box)) }
        vm.setCollection("Pockets")
        vm.close()
        advanceUntilIdle()
        vm.create(IdeaKind.COLLAGE)
        advanceUntilIdle()
        vm.changeCollage { it.copy(items = listOf(CollageItem("a", CollageKind.TEXT, text = "hem"))) }
        vm.close()
        advanceUntilIdle()
        assertEquals(listOf("Pockets"), vm.collections.value)
        assertEquals(2, vm.shown.value.size)
        vm.showCollection("Pockets")
        advanceUntilIdle()
        assertEquals(listOf(IdeaKind.SKETCH), vm.shown.value.map { it.kind })
        // New ideas start in the collection being shown.
        vm.create(IdeaKind.SKETCH)
        advanceUntilIdle()
        assertEquals("Pockets", vm.open.value!!.idea.collection)
    }

    @Test
    fun collageDragIsOneUndoStep() = runTest(dispatcher) {
        vm.create(IdeaKind.COLLAGE)
        advanceUntilIdle()
        val item = CollageItem("a", CollageKind.TEXT, text = "hem", x = 0.1f)
        vm.changeCollage { it.copy(items = listOf(item)) }
        vm.checkpoint()
        vm.updateItem(item.copy(x = 0.2f))
        vm.updateItem(item.copy(x = 0.3f))
        vm.undo()
        assertEquals(0.1f, vm.open.value!!.collage.items.single().x)
        vm.deleteOpen()
        advanceUntilIdle()
        assertFalse(repo.ideas.value.single().deletedAt == null)
    }
}
