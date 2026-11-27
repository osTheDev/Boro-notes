package com.boro.notes

import androidx.lifecycle.SavedStateHandle
import com.boro.notes.data.Note
import com.boro.notes.ui.NotesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createdNoteIsOpenAndAutosaves() = runTest(dispatcher) {
        val repo = FakeNoteRepository()
        val vm = NotesViewModel(repo, SavedStateHandle())

        vm.createNote()
        advanceUntilIdle()
        val id = vm.editor.value!!.id

        vm.onTitleChange("Bomber jacket")
        vm.onBodyChange("Raglan sleeve, ribbed hem")
        advanceTimeBy(NotesViewModel.AUTOSAVE_DELAY_MS / 2)
        assertEquals("", repo.get(id)!!.title) // still debouncing

        advanceUntilIdle()
        assertEquals("Bomber jacket", repo.get(id)!!.title)
        assertEquals("Raglan sleeve, ribbed hem", repo.get(id)!!.body)
        assertEquals(1, repo.saveCount)
    }

    @Test
    fun closingFlushesPendingEdits() = runTest(dispatcher) {
        val repo = FakeNoteRepository(listOf(Note(id = 1, title = "Old")))
        val vm = NotesViewModel(repo, SavedStateHandle())
        vm.open(1)
        advanceUntilIdle()

        vm.onTitleChange("New")
        vm.close()
        advanceUntilIdle()

        assertNull(vm.editor.value)
        assertEquals("New", repo.get(1)!!.title)
    }

    @Test
    fun unsavedDraftSurvivesRecreation() = runTest(dispatcher) {
        val repo = FakeNoteRepository(listOf(Note(id = 1, title = "Saved")))
        val saved = SavedStateHandle()
        val first = NotesViewModel(repo, saved)
        first.open(1)
        advanceUntilIdle()
        first.onTitleChange("Draft not yet saved")

        // Simulate the process being killed before autosave, then restored.
        val restored = NotesViewModel(FakeNoteRepository(listOf(Note(id = 1, title = "Saved"))), saved)
        advanceUntilIdle()

        assertEquals(1L, restored.editor.value!!.id)
        assertEquals("Draft not yet saved", restored.editor.value!!.title)
    }

    @Test
    fun blankNoteIsDiscardedOnClose() = runTest(dispatcher) {
        val repo = FakeNoteRepository()
        val vm = NotesViewModel(repo, SavedStateHandle())
        vm.createNote()
        advanceUntilIdle()
        vm.close()
        advanceUntilIdle()
        assertEquals(0, repo.notes.value.size)

        vm.createNote()
        advanceUntilIdle()
        vm.onBodyChange("keep me")
        vm.close()
        advanceUntilIdle()
        assertEquals(1, repo.notes.value.size)
    }

    @Test
    fun searchFiltersByTitleAndBody() = runTest(dispatcher) {
        val repo = FakeNoteRepository(
            listOf(
                Note(id = 1, title = "Denim", body = "wide leg"),
                Note(id = 2, title = "Shirt", body = "denim collar"),
                Note(id = 3, title = "Coat", body = "wool"),
            )
        )
        val vm = NotesViewModel(repo, SavedStateHandle())
        vm.onQueryChange("denim")
        advanceUntilIdle()

        assertEquals(setOf(1L, 2L), vm.notes.value.map { it.id }.toSet())
    }

    @Test
    fun deleteMovesNoteToTrashAndClosesEditor() = runTest(dispatcher) {
        val repo = FakeNoteRepository(listOf(Note(id = 1, title = "Gone")))
        val vm = NotesViewModel(repo, SavedStateHandle())
        vm.open(1)
        advanceUntilIdle()

        vm.deleteOpenNote()
        advanceUntilIdle()

        assertNull(vm.editor.value)
        // In the Trash (restorable), not in the list.
        assertNotNull(repo.get(1)?.deletedAt)
        assertTrue(vm.notes.value.none { it.id == 1L })

        repo.restore(1)
        advanceUntilIdle()
        assertTrue(vm.notes.value.any { it.id == 1L })
    }
}
