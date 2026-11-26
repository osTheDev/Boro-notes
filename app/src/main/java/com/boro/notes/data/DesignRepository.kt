package com.boro.notes.data

import kotlinx.coroutines.flow.Flow

interface DesignRepository {
    fun observeDesigns(): Flow<List<Design>>
    suspend fun get(id: Long): Design?
    suspend fun create(): Long
    /** Applies [change] to the stored design and bumps its updated time if anything changed. */
    suspend fun update(id: Long, change: (Design) -> Design)
    suspend fun delete(id: Long)
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
}
