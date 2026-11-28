package com.boro.notes.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Where a project on the table is, step by step. */
enum class ProjectStep(val label: String) {
    DESIGN("Design"),
    PATTERN("Pattern making"),
    CUTTING("Cutting"),
    SEWING("Sewing"),
    ITERATIONS("Iterations"),
    FINISHED("Finished"),
}

/** Something being made: its photos live in [ProjectPhoto]; the lists below are JSON (see [ProjectJson]). */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val step: ProjectStep = ProjectStep.DESIGN,
    /** Cover photo file name (in filesDir/images); null uses the newest photo. */
    val cover: String? = null,
    val notes: String = "",
    /** [ProjectTodo] list. */
    val todos: String = "[]",
    /** [Material] list. */
    val materials: String = "[]",
    /** [ProjectLink] list: the designs and sketches this came from. */
    val links: String = "[]",
    /** The finished project this was spun off from. */
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
)

/** A dated progress photo, with notes pinned on it and finger markup. */
@Entity(tableName = "project_photos")
data class ProjectPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val file: String,
    val caption: String = "",
    /** [PhotoPin] list. */
    val pins: String = "[]",
    /** [PhotoMark] list. */
    val marks: String = "[]",
    val takenAt: Long = System.currentTimeMillis(),
)

@Serializable
data class ProjectTodo(val text: String, val done: Boolean = false)

@Serializable
data class Material(
    val name: String = "",
    /** A [MaterialKind] label: Fabric, Hardware or Notions. */
    val kind: String = "Fabric",
    val weight: String = "",
    val weave: String = "",
    val colour: String = "",
    val quantity: String = "",
    val link: String = "",
    val notes: String = "",
)

@Serializable
data class ProjectLink(val kind: LinkKind, val id: Long)

@Serializable
enum class LinkKind { DESIGN, IDEA }

/** A note pinned at ([x], [y]), as fractions of the photo's width and height. */
@Serializable
data class PhotoPin(val x: Float, val y: Float, val text: String)

/** A finger mark on a photo; points are x,y pairs as fractions of the photo's width and height. */
@Serializable
data class PhotoMark(val color: Long, val width: Float, val points: List<Float>)

/** Where a point on a photo (fractions of width and height) lands after the photo turns a quarter. */
fun turnPoint(x: Float, y: Float, clockwise: Boolean): Pair<Float, Float> =
    if (clockwise) (1f - y) to x else y to (1f - x)

fun PhotoPin.turned(clockwise: Boolean): PhotoPin = turnPoint(x, y, clockwise).let { (nx, ny) -> copy(x = nx, y = ny) }

fun PhotoMark.turned(clockwise: Boolean): PhotoMark =
    copy(points = points.chunked(2).flatMap { if (it.size == 2) turnPoint(it[0], it[1], clockwise).toList() else emptyList() })

/** Material categories, each with its own fields. Older "Thread" and "Other" entries count as notions. */
enum class MaterialKind(val label: String) {
    FABRIC("Fabric"), HARDWARE("Hardware"), NOTIONS("Notions");

    companion object {
        fun of(kind: String): MaterialKind = when (kind) {
            "Fabric" -> FABRIC
            "Hardware" -> HARDWARE
            else -> NOTIONS
        }
    }
}

object ProjectJson {
    private val json = Json { ignoreUnknownKeys = true }
    fun todos(s: String): List<ProjectTodo> = runCatching { json.decodeFromString<List<ProjectTodo>>(s) }.getOrDefault(emptyList())
    fun materials(s: String): List<Material> = runCatching { json.decodeFromString<List<Material>>(s) }.getOrDefault(emptyList())
    fun links(s: String): List<ProjectLink> = runCatching { json.decodeFromString<List<ProjectLink>>(s) }.getOrDefault(emptyList())
    fun pins(s: String): List<PhotoPin> = runCatching { json.decodeFromString<List<PhotoPin>>(s) }.getOrDefault(emptyList())
    fun marks(s: String): List<PhotoMark> = runCatching { json.decodeFromString<List<PhotoMark>>(s) }.getOrDefault(emptyList())
    fun encodeTodos(l: List<ProjectTodo>) = json.encodeToString(l)
    fun encodeMaterials(l: List<Material>) = json.encodeToString(l)
    fun encodeLinks(l: List<ProjectLink>) = json.encodeToString(l)
    fun encodePins(l: List<PhotoPin>) = json.encodeToString(l)
    fun encodeMarks(l: List<PhotoMark>) = json.encodeToString(l)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): Project?

    @Insert
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)

    @Query("UPDATE projects SET deletedAt = :at WHERE id = :id")
    suspend fun setDeleted(id: Long, at: Long?)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM projects WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun expired(before: Long): List<Long>

    @Query("SELECT * FROM project_photos WHERE projectId = :projectId ORDER BY takenAt DESC, id DESC")
    fun observePhotos(projectId: Long): Flow<List<ProjectPhoto>>

    @Query("SELECT * FROM project_photos ORDER BY takenAt DESC, id DESC")
    fun observeAllPhotos(): Flow<List<ProjectPhoto>>

    @Query("SELECT * FROM project_photos WHERE id = :id")
    suspend fun getPhoto(id: Long): ProjectPhoto?

    @Insert
    suspend fun insertPhoto(photo: ProjectPhoto): Long

    @Update
    suspend fun updatePhoto(photo: ProjectPhoto)

    @Query("DELETE FROM project_photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Query("DELETE FROM project_photos WHERE projectId = :projectId")
    suspend fun deletePhotosOf(projectId: Long)
}

interface ProjectRepository {
    fun observeProjects(): Flow<List<Project>>
    suspend fun get(id: Long): Project?
    suspend fun create(project: Project = Project()): Long
    /** Applies [change] and bumps the updated time if anything changed. */
    suspend fun update(id: Long, change: (Project) -> Project)
    /** Deletes for good, photos included (blank projects, emptying the Trash). */
    suspend fun delete(id: Long)
    suspend fun trash(id: Long)
    suspend fun restore(id: Long)
    fun observeTrash(): Flow<List<Project>>
    suspend fun purgeTrash(before: Long)

    fun observePhotos(projectId: Long): Flow<List<ProjectPhoto>>
    fun observeAllPhotos(): Flow<List<ProjectPhoto>>
    suspend fun addPhoto(projectId: Long, file: String): Long
    suspend fun updatePhoto(id: Long, change: (ProjectPhoto) -> ProjectPhoto)
    suspend fun deletePhoto(id: Long)
}

class RoomProjectRepository(private val dao: ProjectDao) : ProjectRepository {
    private fun now() = System.currentTimeMillis()
    override fun observeProjects() = dao.observeProjects()
    override suspend fun get(id: Long) = dao.get(id)
    override suspend fun create(project: Project) = dao.insert(project.copy(id = 0))
    override suspend fun update(id: Long, change: (Project) -> Project) {
        val existing = dao.get(id) ?: return
        val updated = change(existing)
        if (updated == existing) return
        dao.update(updated.copy(updatedAt = now()))
    }
    override suspend fun delete(id: Long) {
        dao.deletePhotosOf(id)
        dao.delete(id)
    }
    override suspend fun trash(id: Long) = dao.setDeleted(id, now())
    override suspend fun restore(id: Long) = dao.setDeleted(id, null)
    override fun observeTrash() = dao.observeTrash()
    override suspend fun purgeTrash(before: Long) = dao.expired(before).forEach { delete(it) }

    override fun observePhotos(projectId: Long) = dao.observePhotos(projectId)
    override fun observeAllPhotos() = dao.observeAllPhotos()
    override suspend fun addPhoto(projectId: Long, file: String): Long {
        val id = dao.insertPhoto(ProjectPhoto(projectId = projectId, file = file))
        touch(projectId)
        return id
    }
    override suspend fun updatePhoto(id: Long, change: (ProjectPhoto) -> ProjectPhoto) {
        val existing = dao.getPhoto(id) ?: return
        val updated = change(existing)
        if (updated == existing) return
        dao.updatePhoto(updated)
        touch(existing.projectId)
    }
    override suspend fun deletePhoto(id: Long) {
        val existing = dao.getPhoto(id) ?: return
        dao.deletePhoto(id)
        touch(existing.projectId)
    }
    private suspend fun touch(projectId: Long) {
        dao.get(projectId)?.let { dao.update(it.copy(updatedAt = now())) }
    }
}
