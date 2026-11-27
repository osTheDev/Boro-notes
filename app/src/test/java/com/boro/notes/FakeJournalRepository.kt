package com.boro.notes

import com.boro.notes.data.Journal
import com.boro.notes.data.JournalPage
import com.boro.notes.data.JournalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeJournalRepository : JournalRepository {
    val journals = MutableStateFlow<List<Journal>>(emptyList())
    val pages = MutableStateFlow<List<JournalPage>>(emptyList())
    private var nextId = 1L
    private var clock = 1_000L

    override fun observeJournals(): Flow<List<Journal>> =
        journals.map { all -> all.filter { it.deletedAt == null }.sortedByDescending { it.updatedAt } }
    override suspend fun getJournal(id: Long) = journals.value.find { it.id == id }
    override suspend fun createJournal(title: String, color: Int, pattern: Int): Long {
        val id = nextId++
        journals.value = journals.value + Journal(id = id, title = title, color = color, pattern = pattern, createdAt = ++clock)
        return id
    }
    override suspend fun updateJournal(journal: Journal) {
        journals.value = journals.value.map { if (it.id == journal.id) journal.copy(updatedAt = ++clock) else it }
    }
    override fun observePages(journalId: Long): Flow<List<JournalPage>> = pages.map { all ->
        all.filter { it.journalId == journalId && it.deletedAt == null }.sortedWith(compareByDescending<JournalPage> { it.day }.thenByDescending { it.id })
    }
    override suspend fun getPage(id: Long) = pages.value.find { it.id == id }
    override suspend fun createPage(journalId: Long, day: Long): Long {
        val id = nextId++
        pages.value = pages.value + JournalPage(id = id, journalId = journalId, day = day, createdAt = ++clock)
        return id
    }
    override suspend fun savePage(id: Long, title: String, body: String, content: String?) {
        pages.value = pages.value.map { if (it.id == id) it.copy(title = title, body = body, content = content, updatedAt = ++clock) else it }
    }
    override suspend fun deletePage(id: Long) {
        pages.value = pages.value.filterNot { it.id == id }
    }
    override suspend fun trashJournal(id: Long) {
        val at = ++clock
        journals.value = journals.value.map { if (it.id == id) it.copy(deletedAt = at) else it }
    }
    override suspend fun restoreJournal(id: Long) {
        journals.value = journals.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override suspend fun deleteJournalForever(id: Long) {
        pages.value = pages.value.filterNot { it.journalId == id }
        journals.value = journals.value.filterNot { it.id == id }
    }
    override fun observeTrashedJournals(): Flow<List<Journal>> = journals.map { all -> all.filter { it.deletedAt != null } }
    override suspend fun trashPage(id: Long) {
        val at = ++clock
        pages.value = pages.value.map { if (it.id == id) it.copy(deletedAt = at) else it }
    }
    override suspend fun restorePage(id: Long) {
        pages.value = pages.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override fun observeTrashedPages(): Flow<List<JournalPage>> = pages.map { all -> all.filter { it.deletedAt != null } }
    override suspend fun purgeTrash(before: Long) {
        journals.value.filter { (it.deletedAt ?: Long.MAX_VALUE) < before }.forEach { deleteJournalForever(it.id) }
        pages.value = pages.value.filterNot { (it.deletedAt ?: Long.MAX_VALUE) < before }
    }
}
