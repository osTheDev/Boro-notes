package com.boro.notes

import com.boro.notes.ui.TasksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {
    // A Thursday.
    private val today = LocalDate.of(2026, 11, 26)
    private lateinit var repo: FakeTaskRepository
    private lateinit var vm: TasksViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repo = FakeTaskRepository()
        vm = TasksViewModel(repo) { today }
    }

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 23), vm.weekStart.value)
    }

    @Test fun punchesAreKeptPerWeekAndRepeat() = runTest {
        vm.addHabit("Sketch", color = 1, target = 3)
        val id = vm.habits.value.single().habit.id
        vm.togglePunch(id, 0)
        vm.togglePunch(id, 3)
        assertEquals(listOf(true, false, false, true, false, false, false), vm.habits.value.single().punched)
        assertFalse(vm.habits.value.single().complete)

        // Next week starts blank, and coming back shows the punches again.
        vm.nextWeek()
        assertEquals(0, vm.habits.value.single().count)
        vm.thisWeek()
        assertEquals(2, vm.habits.value.single().count)

        vm.togglePunch(id, 3)
        assertEquals(1, vm.habits.value.single().count)
        vm.togglePunch(id, 4); vm.togglePunch(id, 5)
        assertTrue(vm.habits.value.single().complete)
    }

    @Test fun manyPunchCardsAndReorder() = runTest {
        vm.addHabit("A", 0, 7)
        vm.addHabit("B", 1, 7)
        vm.addHabit("  ", 1, 7)
        assertEquals(listOf("A", "B"), vm.habits.value.map { it.habit.name })
        vm.moveHabit(vm.habits.value[1].habit.id, -1)
        assertEquals(listOf("B", "A"), vm.habits.value.map { it.habit.name })
        vm.deleteHabit(vm.habits.value[0].habit.id)
        assertEquals(listOf("A"), vm.habits.value.map { it.habit.name })
    }

    @Test fun todos() = runTest {
        vm.addTodo("Cut denim")
        vm.addTodo("Buy thread")
        // Newest first.
        assertEquals(listOf("Buy thread", "Cut denim"), vm.todos.value.map { it.text })
        vm.toggleTodo(vm.todos.value.first { it.text == "Buy thread" })
        assertTrue(vm.todos.value.first { it.text == "Buy thread" }.done)
        vm.clearDone()
        assertEquals(listOf("Cut denim"), vm.todos.value.map { it.text })
    }

    @Test fun savingUpListOrdersMovesAndBuys() = runTest {
        vm.addWish("Coat", "$1,299.50")
        vm.addWish("Thread", "4")
        vm.addWish("Bad", "abc")
        vm.addWish("Boots", "180")
        advanceUntilIdle()
        assertEquals(listOf("Coat", "Thread", "Boots"), vm.wishes.value.map { it.name })
        assertEquals(129_950L, vm.wishes.value.first().price)

        vm.setWishSort(com.boro.notes.ui.WishSort.DEAREST)
        advanceUntilIdle()
        assertEquals(listOf("Coat", "Boots", "Thread"), vm.wishes.value.map { it.name })

        // Moving goes back to your own order.
        vm.moveWish(vm.wishes.value.first { it.name == "Boots" }.id, -1)
        advanceUntilIdle()
        assertEquals(com.boro.notes.ui.WishSort.MINE, vm.wishSort.value)
        assertEquals(listOf("Coat", "Boots", "Thread"), vm.wishes.value.map { it.name })

        vm.toggleBought(vm.wishes.value.first { it.name == "Thread" })
        advanceUntilIdle()
        assertEquals(listOf("Thread"), vm.bought.value.map { it.name })
        assertEquals(listOf("Coat", "Boots"), vm.wishes.value.map { it.name })
        vm.clearBought()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), vm.bought.value.map { it.name })
    }

    @Test fun pricesParse() {
        assertEquals(129_950L, com.boro.notes.ui.TasksViewModel.parsePrice("$1,299.50"))
        assertEquals(400L, com.boro.notes.ui.TasksViewModel.parsePrice("4"))
        assertEquals(null, com.boro.notes.ui.TasksViewModel.parsePrice("1.2.3"))
        assertEquals(null, com.boro.notes.ui.TasksViewModel.parsePrice(""))
    }
}
