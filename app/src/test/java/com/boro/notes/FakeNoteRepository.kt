package com.boro.notes

import com.boro.notes.data.Note
import com.boro.notes.data.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeNoteRepository(initial: List<Note> = emptyList()) : NoteRepository {
    val notes = MutableStateFlow(initial)
    var saveCount = 0
        private set
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    private var clock = 1_000L

    override fun observeNotes(query: String): Flow<List<Note>> = notes.map { all ->
        all.filter {
            it.deletedAt == null
        }.filter {
            query.isBlank() || it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
        }.sortedByDescending { it.updatedAt }
    }

    override suspend fun get(id: Long) = notes.value.find { it.id == id }

    override suspend fun create(): Long {
        val id = nextId++
        notes.value = notes.value + Note(id = id, createdAt = ++clock)
        return id
    }

    override suspend fun save(id: Long, title: String, body: String, content: String?) {
        val existing = get(id) ?: return
        if (existing.title == title && existing.body == body && existing.content == content) return
        saveCount++
        notes.value = notes.value.map {
            if (it.id == id) it.copy(title = title, body = body, content = content, updatedAt = ++clock) else it
        }
    }

    override suspend fun delete(id: Long) {
        notes.value = notes.value.filterNot { it.id == id }
    }

    override suspend fun trash(id: Long) {
        val at = ++clock
        notes.value = notes.value.map { if (it.id == id) it.copy(deletedAt = at) else it }
    }

    override suspend fun restore(id: Long) {
        notes.value = notes.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }

    override fun observeTrash(): Flow<List<Note>> = notes.map { all -> all.filter { it.deletedAt != null } }

    override suspend fun purgeTrash(before: Long) {
        notes.value = notes.value.filterNot { (it.deletedAt ?: Long.MAX_VALUE) < before }
    }
}
