package com.boro.notes

import com.boro.notes.data.Project
import com.boro.notes.data.ProjectPhoto
import com.boro.notes.data.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeProjectRepository(initial: List<Project> = emptyList()) : ProjectRepository {
    val projects = MutableStateFlow(initial)
    val photos = MutableStateFlow<List<ProjectPhoto>>(emptyList())
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
    private var nextPhoto = 1L
    private var clock = 1_000L

    override fun observeProjects(): Flow<List<Project>> = projects.map { it.filter { p -> p.deletedAt == null }.sortedByDescending { p -> p.updatedAt } }
    override suspend fun get(id: Long) = projects.value.find { it.id == id }
    override suspend fun create(project: Project): Long {
        val id = nextId++
        val at = ++clock
        projects.value = projects.value + project.copy(id = id, createdAt = at, updatedAt = at)
        return id
    }
    override suspend fun update(id: Long, change: (Project) -> Project) {
        projects.value = projects.value.map { if (it.id != id) it else change(it).let { u -> if (u == it) it else u.copy(updatedAt = ++clock) } }
    }
    override suspend fun delete(id: Long) {
        projects.value = projects.value.filterNot { it.id == id }
        photos.value = photos.value.filterNot { it.projectId == id }
    }
    override suspend fun trash(id: Long) {
        val at = ++clock
        projects.value = projects.value.map { if (it.id == id) it.copy(deletedAt = at) else it }
    }
    override suspend fun restore(id: Long) {
        projects.value = projects.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }
    override fun observeTrash(): Flow<List<Project>> = projects.map { all -> all.filter { it.deletedAt != null } }
    override suspend fun purgeTrash(before: Long) {
        projects.value.filter { (it.deletedAt ?: Long.MAX_VALUE) < before }.forEach { delete(it.id) }
    }
    override fun observePhotos(projectId: Long): Flow<List<ProjectPhoto>> =
        photos.map { all -> all.filter { it.projectId == projectId }.sortedByDescending { it.takenAt } }
    override fun observeAllPhotos(): Flow<List<ProjectPhoto>> = photos.map { all -> all.sortedByDescending { it.takenAt } }
    override suspend fun addPhoto(projectId: Long, file: String): Long {
        val id = nextPhoto++
        photos.value = photos.value + ProjectPhoto(id = id, projectId = projectId, file = file, takenAt = ++clock)
        return id
    }
    override suspend fun updatePhoto(id: Long, change: (ProjectPhoto) -> ProjectPhoto) {
        photos.value = photos.value.map { if (it.id == id) change(it) else it }
    }
    override suspend fun deletePhoto(id: Long) {
        photos.value = photos.value.filterNot { it.id == id }
    }
}
