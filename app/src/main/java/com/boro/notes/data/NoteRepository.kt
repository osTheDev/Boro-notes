package com.boro.notes.data

import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(query: String): Flow<List<Note>>
    suspend fun get(id: Long): Note?
    suspend fun create(): Long
    suspend fun save(id: Long, title: String, body: String)
    suspend fun delete(id: Long)
}

class RoomNoteRepository(private val dao: NoteDao) : NoteRepository {
    override fun observeNotes(query: String) = dao.observe(query.trim())
    override suspend fun get(id: Long) = dao.get(id)
    override suspend fun create(): Long = dao.insert(Note())
    override suspend fun save(id: Long, title: String, body: String) {
        val existing = dao.get(id) ?: return
        if (existing.title == title && existing.body == body) return
        dao.update(existing.copy(title = title, body = body, updatedAt = System.currentTimeMillis()))
    }
    override suspend fun delete(id: Long) = dao.delete(id)
}
