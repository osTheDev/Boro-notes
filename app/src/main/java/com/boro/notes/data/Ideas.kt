package com.boro.notes.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class IdeaKind { SKETCH, COLLAGE }

/**
 * A quick idea on the Sketch board: a loose sketch or a collage. [content] is a
 * [com.boro.notes.studio.SketchPad] or [com.boro.notes.studio.Collage] as JSON.
 */
@Entity(tableName = "ideas")
data class Idea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: IdeaKind = IdeaKind.SKETCH,
    val title: String = "",
    /** A one-line note shown under the thumbnail. */
    val note: String = "",
    /** The named collection it's grouped in, if any. */
    val collection: String? = null,
    val content: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
)

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Idea>>

    @Query("SELECT * FROM ideas WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Idea>>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun get(id: Long): Idea?

    @Insert
    suspend fun insert(idea: Idea): Long

    @Update
    suspend fun update(idea: Idea)

    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE ideas SET deletedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: Long, at: Long?)

    @Query("DELETE FROM ideas WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purge(before: Long)
}

interface IdeaRepository {
    fun observeIdeas(): Flow<List<Idea>>
    suspend fun get(id: Long): Idea?
    suspend fun create(idea: Idea): Long
    suspend fun update(id: Long, change: (Idea) -> Idea)
    suspend fun delete(id: Long)
    suspend fun trash(id: Long)
    suspend fun restore(id: Long)
    fun observeTrash(): Flow<List<Idea>>
    suspend fun purgeTrash(before: Long)
}

class RoomIdeaRepository(private val dao: IdeaDao) : IdeaRepository {
    override fun observeIdeas() = dao.observeAll()
    override suspend fun get(id: Long) = dao.get(id)
    override suspend fun create(idea: Idea) = dao.insert(idea.copy(id = 0))
    override suspend fun update(id: Long, change: (Idea) -> Idea) {
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
