package com.boro.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boro.notes.data.Habit
import com.boro.notes.data.TaskRepository
import com.boro.notes.data.Todo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A punch card for one week: which of Mon..Sun are punched. */
data class HabitWeek(val habit: Habit, val punched: List<Boolean>) {
    val count get() = punched.count { it }
    val complete get() = count >= habit.target
}

class TasksViewModel(
    private val repo: TaskRepository,
    /** Today's date; replaceable in tests. */
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _weekStart = MutableStateFlow(mondayOf(today()))
    /** The Monday of the week being shown. */
    val weekStart: StateFlow<LocalDate> = _weekStart.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val habits: StateFlow<List<HabitWeek>> = _weekStart.flatMapLatest { start ->
        val from = start.toEpochDay()
        combine(repo.observeHabits(), repo.observeChecks(from, from + 6)) { habits, checks ->
            val punched = checks.groupBy({ it.habitId }, { it.day })
            habits.map { h ->
                val days = punched[h.id].orEmpty().toSet()
                HabitWeek(h, (0..6).map { (from + it) in days })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todos: StateFlow<List<Todo>> = repo.observeTodos()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun todayDate(): LocalDate = today()
    val isThisWeek get() = _weekStart.value == mondayOf(today())

    fun previousWeek() { _weekStart.value = _weekStart.value.minusWeeks(1) }
    fun nextWeek() { _weekStart.value = _weekStart.value.plusWeeks(1) }
    fun thisWeek() { _weekStart.value = mondayOf(today()) }

    /** Punches or un-punches [habitId] on weekday [index] (0 = Monday) of the shown week. */
    fun togglePunch(habitId: Long, index: Int) {
        val week = habits.value.firstOrNull { it.habit.id == habitId } ?: return
        val day = _weekStart.value.plusDays(index.toLong()).toEpochDay()
        viewModelScope.launch { repo.setCheck(habitId, day, !week.punched[index]) }
    }

    fun addHabit(name: String, color: Int, target: Int) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.addHabit(name.trim(), color, target.coerceIn(1, 7)) }
    }

    fun updateHabit(habit: Habit) {
        if (habit.name.isBlank()) return
        viewModelScope.launch { repo.updateHabit(habit.copy(name = habit.name.trim(), target = habit.target.coerceIn(1, 7))) }
    }

    fun deleteHabit(id: Long) = viewModelScope.launch { repo.deleteHabit(id) }

    /** Moves a punch card up (-1) or down (+1). */
    fun moveHabit(id: Long, by: Int) {
        val list = habits.value.map { it.habit }.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        viewModelScope.launch { list.forEachIndexed { pos, h -> if (h.position != pos) repo.updateHabit(h.copy(position = pos)) } }
    }

    fun addTodo(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.addTodo(text.trim()) }
    }

    fun toggleTodo(todo: Todo) = viewModelScope.launch {
        val done = !todo.done
        repo.updateTodos(listOf(todo.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)))
    }

    fun editTodo(todo: Todo, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.updateTodos(listOf(todo.copy(text = text.trim()))) }
    }

    fun deleteTodo(id: Long) = viewModelScope.launch { repo.deleteTodo(id) }

    fun clearDone() = viewModelScope.launch { repo.clearDone() }

    /** Moves an open to-do up (-1) or down (+1) among the open ones. */
    fun moveTodo(id: Long, by: Int) {
        val open = todos.value.filterNot { it.done }.toMutableList()
        val i = open.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in open.indices) return
        open.add(j, open.removeAt(i))
        val changed = open.mapIndexedNotNull { pos, t -> if (t.position != pos) t.copy(position = pos) else null }
        viewModelScope.launch { repo.updateTodos(changed) }
    }

    companion object {
        fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        fun factory(repo: TaskRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TasksViewModel(repo) }
        }
    }
}
