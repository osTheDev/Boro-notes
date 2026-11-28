package com.boro.notes

import com.boro.notes.data.Habit
import com.boro.notes.data.HabitCheck
import com.boro.notes.data.TaskRepository
import com.boro.notes.data.Todo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTaskRepository : TaskRepository {
    val habits = MutableStateFlow<List<Habit>>(emptyList())
    val checks = MutableStateFlow<Set<HabitCheck>>(emptySet())
    val todos = MutableStateFlow<List<Todo>>(emptyList())
    private var nextId = 1L

    override fun observeHabits(): Flow<List<Habit>> = habits.map { it.sortedWith(compareBy({ h -> h.position }, { h -> h.id })) }
    override fun observeChecks(fromDay: Long, toDay: Long): Flow<List<HabitCheck>> =
        checks.map { all -> all.filter { it.day in fromDay..toDay } }
    override suspend fun addHabit(name: String, color: Int, target: Int): Long {
        val id = nextId++
        habits.value = habits.value + Habit(id = id, name = name, color = color, target = target, position = habits.value.size)
        return id
    }
    override suspend fun updateHabit(habit: Habit) { habits.value = habits.value.map { if (it.id == habit.id) habit else it } }
    override suspend fun deleteHabit(id: Long) {
        habits.value = habits.value.filterNot { it.id == id }
        checks.value = checks.value.filterNot { it.habitId == id }.toSet()
    }
    override suspend fun setCheck(habitId: Long, day: Long, done: Boolean) {
        val c = HabitCheck(habitId, day)
        checks.value = if (done) checks.value + c else checks.value - c
    }

    override fun observeTodos(): Flow<List<Todo>> = todos.map { it.sortedWith(compareBy({ t -> t.position }, { t -> t.id })) }
    override suspend fun addTodo(text: String): Long {
        val id = nextId++
        todos.value = todos.value + Todo(id = id, text = text, position = (todos.value.minOfOrNull { it.position } ?: 1) - 1)
        return id
    }
    override suspend fun updateTodos(todos: List<Todo>) {
        val byId = todos.associateBy { it.id }
        this.todos.value = this.todos.value.map { byId[it.id] ?: it }
    }
    override suspend fun deleteTodo(id: Long) { todos.value = todos.value.filterNot { it.id == id } }
    override suspend fun clearDone() { todos.value = todos.value.filterNot { it.done } }
}
