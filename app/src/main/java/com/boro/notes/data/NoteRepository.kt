package com.boro.notes.data

import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotes(query: String): Flow<List<Note>>
    suspend fun get(id: Long): Note?
    suspend fun create(): Long
    /** Saves the note; [content] is the formatted note (null keeps plain text only). */
    suspend fun save(id: Long, title: String, body: String, content: String? = null)
    /** Deletes for good (used for blank notes and emptying the Trash). */
    suspend fun delete(id: Long)
    /** Moves to the Trash, where it stays restorable for a week. */
    suspend fun trash(id: Long)
    suspend fun restore(id: Long)
    fun observeTrash(): Flow<List<Note>>
    /** Deletes notes trashed before [before] (millis). */
    suspend fun purgeTrash(before: Long)
}

class RoomNoteRepository(private val dao: NoteDao) : NoteRepository {
    override fun observeNotes(query: String) = dao.observe(query.trim())
    override suspend fun get(id: Long) = dao.get(id)
    override suspend fun create(): Long = dao.insert(Note())
    override suspend fun save(id: Long, title: String, body: String, content: String?) {
        val existing = dao.get(id) ?: return
        if (existing.title == title && existing.body == body && existing.content == content) return
        dao.update(existing.copy(title = title, body = body, content = content, updatedAt = System.currentTimeMillis()))
    }
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun trash(id: Long) = dao.setDeleted(id, System.currentTimeMillis())
    override suspend fun restore(id: Long) = dao.setDeleted(id, null)
    override fun observeTrash() = dao.observeTrash()
    override suspend fun purgeTrash(before: Long) = dao.purge(before)
}
