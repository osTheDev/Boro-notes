package com.boro.notes

import com.boro.notes.data.Design
import com.boro.notes.data.DesignRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeDesignRepository(initial: List<Design> = emptyList()) : DesignRepository {
    val designs = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    private var clock = 1_000L

    override fun observeDesigns(): Flow<List<Design>> = designs.map { it.filter { d -> d.deletedAt == null }.sortedByDescending { d -> d.updatedAt } }
    override suspend fun get(id: Long) = designs.value.find { it.id == id }
    override suspend fun create(): Long {
        val id = nextId++
        designs.value = designs.value + Design(id = id, createdAt = ++clock)
        return id
    }
    override suspend fun update(id: Long, change: (Design) -> Design) {
        designs.value = designs.value.map {
            if (it.id != id) it else change(it).let { u -> if (u == it) it else u.copy(updatedAt = ++clock) }
        }
    }
    override suspend fun delete(id: Long) {
        designs.value = designs.value.filterNot { it.id == id }
    }
    override suspend fun trash(id: Long) {
        val at = ++clock
        designs.value = designs.value.map { if (it.id == id) it.copy(deletedAt = at) else it }
    }
    override suspend fun restore(id: Long) {
        designs.value = designs.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override fun observeTrash(): Flow<List<Design>> = designs.map { all -> all.filter { it.deletedAt != null } }
    override suspend fun purgeTrash(before: Long) {
        designs.value = designs.value.filterNot { (it.deletedAt ?: Long.MAX_VALUE) < before }
    }
}
