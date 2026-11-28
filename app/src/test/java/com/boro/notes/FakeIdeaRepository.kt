package com.boro.notes

import com.boro.notes.data.Idea
import com.boro.notes.data.IdeaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeIdeaRepository(initial: List<Idea> = emptyList()) : IdeaRepository {
    val ideas = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    private var clock = 1_000L

    override fun observeIdeas(): Flow<List<Idea>> = ideas.map { it.filter { i -> i.deletedAt == null }.sortedByDescending { i -> i.createdAt } }
    override suspend fun get(id: Long) = ideas.value.find { it.id == id }
    override suspend fun create(idea: Idea): Long {
        val id = nextId++
        val at = ++clock
        ideas.value = ideas.value + idea.copy(id = id, createdAt = at, updatedAt = at)
        return id
    }
    override suspend fun update(id: Long, change: (Idea) -> Idea) {
        ideas.value = ideas.value.map { if (it.id != id) it else change(it).let { u -> if (u == it) it else u.copy(updatedAt = ++clock) } }
    }
    override suspend fun delete(id: Long) {
        ideas.value = ideas.value.filterNot { it.id == id }
    }
    override suspend fun trash(id: Long) {
        val at = ++clock
        ideas.value = ideas.value.map { if (it.id == id) it.copy(deletedAt = at) else it }
    }
    override suspend fun restore(id: Long) {
        ideas.value = ideas.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override fun observeTrash(): Flow<List<Idea>> = ideas.map { all -> all.filter { it.deletedAt != null } }
    override suspend fun purgeTrash(before: Long) {
        ideas.value = ideas.value.filterNot { (it.deletedAt ?: Long.MAX_VALUE) < before }
    }
}
