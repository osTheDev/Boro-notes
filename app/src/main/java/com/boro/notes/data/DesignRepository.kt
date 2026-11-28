package com.boro.notes.data

import kotlinx.coroutines.flow.Flow

interface DesignRepository {
    fun observeDesigns(): Flow<List<Design>>
    suspend fun get(id: Long): Design?
    suspend fun create(): Long
    /** Applies [change] to the stored design and bumps its updated time if anything changed. */
    suspend fun update(id: Long, change: (Design) -> Design)
    /** Deletes for good (blank designs, emptying the Trash). */
    suspend fun delete(id: Long)
    /** Moves to the Trash, restorable for a week. */
    suspend fun trash(id: Long)
    suspend fun restore(id: Long)
    fun observeTrash(): Flow<List<Design>>
    suspend fun purgeTrash(before: Long)
}

class RoomDesignRepository(private val dao: DesignDao) : DesignRepository {
    override fun observeDesigns() = dao.observeAll()
    override suspend fun get(id: Long) = dao.get(id)
    override suspend fun create(): Long = dao.insert(Design())
    override suspend fun update(id: Long, change: (Design) -> Design) {
        val existing = dao.get(id) ?: return
        val updated = change(existing)
        if (updated == existing) return
        dao.update(updated.copy(updatedAt = System.currentTimeMillis()))
    }
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun trash(id: Long) = dao.setDeleted(id, System.currentTimeMillis())
    override suspend fun restore(id: Long) = dao.setDeleted(id, null)
    override fun observeTrash() = dao.observeTrash()
    override suspend fun purgeTrash(before: Long) = dao.purge(before)
}
