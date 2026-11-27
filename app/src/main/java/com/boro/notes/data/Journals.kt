package com.boro.notes.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A journal (book) on the shelf. */
@Entity(tableName = "journals")
data class Journal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    /** Index into the cover colour palette. */
    val color: Int = 0,
    /** Index into the cover sashiko patterns. */
    val pattern: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
)

/** A dated page in a journal. Formatted like a note (see [com.boro.notes.notes.NoteDoc]). */
@Entity(tableName = "journal_pages")
data class JournalPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val journalId: Long,
    /** The page's date, as days since 1970-01-01. */
    val day: Long,
    val title: String = "",
    val body: String = "",
    val content: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
)

@Dao
interface JournalDao {
    @Query("SELECT * FROM journals WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeJournals(): Flow<List<Journal>>

    @Query("SELECT * FROM journals WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedJournals(): Flow<List<Journal>>

    @Query("SELECT * FROM journals WHERE id = :id")
    suspend fun getJournal(id: Long): Journal?

    @Insert
    suspend fun insertJournal(journal: Journal): Long

    @Update
    suspend fun updateJournal(journal: Journal)

    @Query("UPDATE journals SET deletedAt = :at WHERE id = :id")
    suspend fun setJournalDeleted(id: Long, at: Long?)

    @Query("DELETE FROM journals WHERE id = :id")
    suspend fun deleteJournal(id: Long)

    @Query("DELETE FROM journal_pages WHERE journalId = :id")
    suspend fun deletePagesOf(id: Long)

    @Query("SELECT * FROM journal_pages WHERE journalId = :journalId AND deletedAt IS NULL ORDER BY day DESC, id DESC")
    fun observePages(journalId: Long): Flow<List<JournalPage>>

    @Query("SELECT * FROM journal_pages WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashedPages(): Flow<List<JournalPage>>

    @Query("SELECT * FROM journal_pages WHERE id = :id")
    suspend fun getPage(id: Long): JournalPage?

    @Insert
    suspend fun insertPage(page: JournalPage): Long

    @Update
    suspend fun updatePage(page: JournalPage)

    @Query("UPDATE journal_pages SET deletedAt = :at WHERE id = :id")
    suspend fun setPageDeleted(id: Long, at: Long?)

    @Query("DELETE FROM journal_pages WHERE id = :id")
    suspend fun deletePage(id: Long)

    @Query("SELECT id FROM journals WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun expiredJournals(before: Long): List<Long>

    @Query("DELETE FROM journal_pages WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgePages(before: Long)
}

interface JournalRepository {
    fun observeJournals(): Flow<List<Journal>>
    suspend fun getJournal(id: Long): Journal?
    suspend fun createJournal(title: String, color: Int, pattern: Int): Long
    suspend fun updateJournal(journal: Journal)
    fun observePages(journalId: Long): Flow<List<JournalPage>>
    suspend fun getPage(id: Long): JournalPage?
    suspend fun createPage(journalId: Long, day: Long): Long
    suspend fun savePage(id: Long, title: String, body: String, content: String?)
    /** Deletes a page for good (blank pages). */
    suspend fun deletePage(id: Long)

    suspend fun trashJournal(id: Long)
    suspend fun restoreJournal(id: Long)
    suspend fun deleteJournalForever(id: Long)
    fun observeTrashedJournals(): Flow<List<Journal>>
    suspend fun trashPage(id: Long)
    suspend fun restorePage(id: Long)
    fun observeTrashedPages(): Flow<List<JournalPage>>
    suspend fun purgeTrash(before: Long)
}

class RoomJournalRepository(private val dao: JournalDao) : JournalRepository {
    private fun now() = System.currentTimeMillis()
    override fun observeJournals() = dao.observeJournals()
    override suspend fun getJournal(id: Long) = dao.getJournal(id)
    override suspend fun createJournal(title: String, color: Int, pattern: Int) =
        dao.insertJournal(Journal(title = title, color = color, pattern = pattern))
    override suspend fun updateJournal(journal: Journal) = dao.updateJournal(journal.copy(updatedAt = now()))
    override fun observePages(journalId: Long) = dao.observePages(journalId)
    override suspend fun getPage(id: Long) = dao.getPage(id)
    override suspend fun createPage(journalId: Long, day: Long): Long {
        val id = dao.insertPage(JournalPage(journalId = journalId, day = day))
        dao.getJournal(journalId)?.let { dao.updateJournal(it.copy(updatedAt = now())) }
        return id
    }
    override suspend fun savePage(id: Long, title: String, body: String, content: String?) {
        val p = dao.getPage(id) ?: return
        if (p.title == title && p.body == body && p.content == content) return
        dao.updatePage(p.copy(title = title, body = body, content = content, updatedAt = now()))
        dao.getJournal(p.journalId)?.let { dao.updateJournal(it.copy(updatedAt = now())) }
    }
    override suspend fun deletePage(id: Long) = dao.deletePage(id)

    override suspend fun trashJournal(id: Long) = dao.setJournalDeleted(id, now())
    override suspend fun restoreJournal(id: Long) = dao.setJournalDeleted(id, null)
    override suspend fun deleteJournalForever(id: Long) {
        dao.deletePagesOf(id)
        dao.deleteJournal(id)
    }
    override fun observeTrashedJournals() = dao.observeTrashedJournals()
    override suspend fun trashPage(id: Long) = dao.setPageDeleted(id, now())
    override suspend fun restorePage(id: Long) = dao.setPageDeleted(id, null)
    override fun observeTrashedPages() = dao.observeTrashedPages()
    override suspend fun purgeTrash(before: Long) {
        dao.expiredJournals(before).forEach { deleteJournalForever(it) }
        dao.purgePages(before)
    }
}
