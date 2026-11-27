package com.boro.notes

import com.boro.notes.data.Note
import com.boro.notes.ui.JournalViewModel
import com.boro.notes.ui.TrashKind
import com.boro.notes.ui.TrashViewModel
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 11, 27)
    private lateinit var repo: FakeJournalRepository
    private lateinit var vm: JournalViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeJournalRepository()
        vm = JournalViewModel(repo) { today }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun newJournalOpensWithTodaysPage() = runTest(dispatcher) {
        vm.createJournal("  Field guide ", color = 2, pattern = 1)
        advanceUntilIdle()
        val book = vm.journals.value.single()
        assertEquals("Field guide", book.title)
        assertEquals(book.id, vm.openId.value)

        vm.openFirstPage()
        advanceUntilIdle()
        val page = vm.page.value
        assertNotNull(page)
        assertEquals(today, vm.dayOfOpenPage())
        // New pages start on notebook paper.
        assertTrue(page!!.content!!.contains("CREAM"))
    }

    @Test fun pagesSaveAndTodayIsReused() = runTest(dispatcher) {
        vm.createJournal("Days", 0, 0)
        advanceUntilIdle()
        vm.newPageToday()
        advanceUntilIdle()
        val id = vm.page.value!!.id
        vm.onTitleChange("Moss")
        vm.onDocChange("{}", "green and soft")
        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertEquals("Moss", repo.getPage(id)!!.title)

        // Asking for today's page again opens the same page.
        vm.newPageToday()
        advanceUntilIdle()
        assertEquals(id, vm.page.value!!.id)
        assertEquals(1, vm.pages.value.size)
    }

    @Test fun blankPageIsDroppedWhenLeft() = runTest(dispatcher) {
        vm.createJournal("Days", 0, 0)
        advanceUntilIdle()
        vm.newPageToday()
        advanceUntilIdle()
        vm.closeBook()
        advanceUntilIdle()
        assertTrue(repo.pages.value.isEmpty())
        assertNull(vm.openId.value)
    }

    @Test fun trashAndRestore() = runTest(dispatcher) {
        val notes = FakeNoteRepository(listOf(Note(id = 1, title = "Old note")))
        val designs = FakeDesignRepository()
        val trash = TrashViewModel(notes, designs, repo)
        vm.createJournal("Days", 0, 0)
        advanceUntilIdle()
        vm.newPageToday()
        advanceUntilIdle()
        vm.onTitleChange("Rain")
        vm.trashOpenPage()
        val book = vm.journals.value.single().id
        vm.trashJournal(book)
        notes.trash(1)
        advanceUntilIdle()

        assertTrue(vm.journals.value.isEmpty())
        assertEquals(setOf(TrashKind.NOTE, TrashKind.JOURNAL, TrashKind.PAGE), trash.items.value.map { it.kind }.toSet())

        trash.restore(trash.items.value.first { it.kind == TrashKind.JOURNAL })
        trash.restore(trash.items.value.first { it.kind == TrashKind.PAGE })
        advanceUntilIdle()
        assertEquals(1, vm.journals.value.size)
        assertEquals(listOf("Rain"), repo.pages.value.map { it.title })

        trash.emptyTrash()
        advanceUntilIdle()
        assertTrue(trash.items.value.isEmpty())
        assertNull(notes.get(1))
    }

    @Test fun daysLeftCountsDown() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(7, TrashViewModel.daysLeft(0, now = 1))
        assertEquals(1, TrashViewModel.daysLeft(0, now = 6 * day + 1))
        assertEquals(0, TrashViewModel.daysLeft(0, now = 8 * day))
    }
}
