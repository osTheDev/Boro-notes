package com.boro.notes.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A weekly punch card: something to do on some or all days of every week. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Index into the punch card colour palette. */
    val color: Int = 0,
    /** Punches per week that count as a full week (1 to 7). */
    val target: Int = 7,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One punch: [habitId] was done on [day] (days since 1970-01-01, local date). */
@Entity(tableName = "habit_checks", primaryKeys = ["habitId", "day"])
data class HabitCheck(val habitId: Long, val day: Long)

/** An item on the to-do list. */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val done: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val doneAt: Long? = null,
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM habits ORDER BY position, id")
    fun observeHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habit_checks WHERE day BETWEEN :from AND :to")
    fun observeChecks(from: Long, to: Long): Flow<List<HabitCheck>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM habits")
    suspend fun nextHabitPosition(): Int

    @Insert
    suspend fun insertHabit(habit: Habit): Long

    @Update
    suspend fun updateHabit(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitRow(id: Long)

    @Query("DELETE FROM habit_checks WHERE habitId = :id")
    suspend fun deleteChecksFor(id: Long)

    @Transaction
    suspend fun deleteHabit(id: Long) {
        deleteChecksFor(id)
        deleteHabitRow(id)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheck(check: HabitCheck)

    @Query("DELETE FROM habit_checks WHERE habitId = :habitId AND day = :day")
    suspend fun deleteCheck(habitId: Long, day: Long)

    @Query("SELECT * FROM todos ORDER BY position, id")
    fun observeTodos(): Flow<List<Todo>>

    @Query("SELECT COALESCE(MIN(position), 1) - 1 FROM todos")
    suspend fun firstTodoPosition(): Int

    @Insert
    suspend fun insertTodo(todo: Todo): Long

    @Update
    suspend fun updateTodos(todos: List<Todo>)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteTodo(id: Long)

    @Query("DELETE FROM todos WHERE done = 1")
    suspend fun clearDone()
}

interface TaskRepository {
    fun observeHabits(): Flow<List<Habit>>
    fun observeChecks(fromDay: Long, toDay: Long): Flow<List<HabitCheck>>
    suspend fun addHabit(name: String, color: Int, target: Int): Long
    suspend fun updateHabit(habit: Habit)
    suspend fun deleteHabit(id: Long)
    suspend fun setCheck(habitId: Long, day: Long, done: Boolean)

    fun observeTodos(): Flow<List<Todo>>
    /** New items go to the top of the list. */
    suspend fun addTodo(text: String): Long
    suspend fun updateTodos(todos: List<Todo>)
    suspend fun deleteTodo(id: Long)
    suspend fun clearDone()
}

class RoomTaskRepository(private val dao: TaskDao) : TaskRepository {
    override fun observeHabits() = dao.observeHabits()
    override fun observeChecks(fromDay: Long, toDay: Long) = dao.observeChecks(fromDay, toDay)
    override suspend fun addHabit(name: String, color: Int, target: Int) =
        dao.insertHabit(Habit(name = name, color = color, target = target, position = dao.nextHabitPosition()))
    override suspend fun updateHabit(habit: Habit) = dao.updateHabit(habit)
    override suspend fun deleteHabit(id: Long) = dao.deleteHabit(id)
    override suspend fun setCheck(habitId: Long, day: Long, done: Boolean) =
        if (done) dao.insertCheck(HabitCheck(habitId, day)) else dao.deleteCheck(habitId, day)

    override fun observeTodos() = dao.observeTodos()
    override suspend fun addTodo(text: String) = dao.insertTodo(Todo(text = text, position = dao.firstTodoPosition()))
    override suspend fun updateTodos(todos: List<Todo>) = dao.updateTodos(todos)
    override suspend fun deleteTodo(id: Long) = dao.deleteTodo(id)
    override suspend fun clearDone() = dao.clearDone()
}
