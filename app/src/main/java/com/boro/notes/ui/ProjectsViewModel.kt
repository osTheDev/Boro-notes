package com.boro.notes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.boro.notes.data.Design
import com.boro.notes.data.DesignRepository
import com.boro.notes.data.Idea
import com.boro.notes.data.IdeaRepository
import com.boro.notes.data.LinkKind
import com.boro.notes.data.Material
import com.boro.notes.data.PhotoMark
import com.boro.notes.data.PhotoPin
import com.boro.notes.data.Project
import com.boro.notes.data.ProjectJson
import com.boro.notes.data.ProjectLink
import com.boro.notes.data.ProjectPhoto
import com.boro.notes.data.ProjectRepository
import com.boro.notes.data.ProjectStep
import com.boro.notes.data.ProjectTodo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The open project with its lists decoded. */
data class ProjectDraft(
    val project: Project,
    val todos: List<ProjectTodo> = ProjectJson.todos(project.todos),
    val materials: List<Material> = ProjectJson.materials(project.materials),
    val links: List<ProjectLink> = ProjectJson.links(project.links),
)

/** Projects on the table: steps, dated photos with pinned notes, to-dos, materials, links and spin-offs. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectsViewModel(
    private val repo: ProjectRepository,
    private val designs: DesignRepository,
    private val ideas: IdeaRepository,
) : ViewModel() {
    val projects: StateFlow<List<Project>> = repo.observeProjects().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Newest photo per project, for covers. */
    val latestPhotos: StateFlow<Map<Long, String>> = repo.observeAllPhotos()
        .map { all -> all.groupBy { it.projectId }.mapValues { (_, p) -> p.first().file } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val allDesigns: StateFlow<List<Design>> = designs.observeDesigns().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val allIdeas: StateFlow<List<Idea>> = ideas.observeIdeas().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _open = MutableStateFlow<ProjectDraft?>(null)
    val open: StateFlow<ProjectDraft?> = _open.asStateFlow()

    private val openId = MutableStateFlow<Long?>(null)
    val photos: StateFlow<List<ProjectPhoto>> = openId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observePhotos(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var saveJob: Job? = null

    fun create() {
        leaveCurrent()
        viewModelScope.launch {
            val id = repo.create(Project())
            load(id)
        }
    }

    fun openProject(id: Long) {
        if (_open.value?.project?.id == id) return
        leaveCurrent()
        viewModelScope.launch { load(id) }
    }

    fun close() {
        leaveCurrent()
        _open.value = null
        openId.value = null
    }

    fun rename(name: String) = edit { it.copy(project = it.project.copy(name = name)) }
    fun setNotes(notes: String) = edit { it.copy(project = it.project.copy(notes = notes)) }
    fun setStep(step: ProjectStep) = edit(now = true) { it.copy(project = it.project.copy(step = step)) }
    fun setCover(file: String?) = edit(now = true) { it.copy(project = it.project.copy(cover = file)) }

    fun addTodo(text: String) {
        if (text.isBlank()) return
        edit(now = true) { it.copy(todos = it.todos + ProjectTodo(text.trim())) }
    }
    fun toggleTodo(index: Int) = edit(now = true) { d ->
        d.copy(todos = d.todos.mapIndexed { i, t -> if (i == index) t.copy(done = !t.done) else t })
    }
    fun removeTodo(index: Int) = edit(now = true) { d -> d.copy(todos = d.todos.filterIndexed { i, _ -> i != index }) }

    /** Adds ([index] null) or replaces a material. */
    fun saveMaterial(index: Int?, material: Material) = edit(now = true) { d ->
        d.copy(materials = if (index == null) d.materials + material else d.materials.mapIndexed { i, m -> if (i == index) material else m })
    }
    fun removeMaterial(index: Int) = edit(now = true) { d -> d.copy(materials = d.materials.filterIndexed { i, _ -> i != index }) }

    fun toggleLink(link: ProjectLink) = edit(now = true) { d ->
        d.copy(links = if (link in d.links) d.links - link else d.links + link)
    }

    fun addPhoto(file: String) {
        val id = _open.value?.project?.id ?: return
        viewModelScope.launch { repo.addPhoto(id, file) }
    }

    /** Saves a photo's caption, pins and marks; [file] changes when the photo was turned. */
    fun savePhoto(id: Long, caption: String, pins: List<PhotoPin>, marks: List<PhotoMark>, file: String? = null, oldFile: String? = null) {
        if (file != null && oldFile != null && file != oldFile && _open.value?.project?.cover == oldFile) setCover(file)
        viewModelScope.launch {
            repo.updatePhoto(id) {
                it.copy(caption = caption, pins = ProjectJson.encodePins(pins), marks = ProjectJson.encodeMarks(marks), file = file ?: it.file)
            }
        }
    }

    fun deletePhoto(photo: ProjectPhoto) {
        if (_open.value?.project?.cover == photo.file) setCover(null)
        viewModelScope.launch { repo.deletePhoto(photo.id) }
    }

    /** Moves the open project to the Trash. */
    fun deleteOpen() {
        val d = _open.value ?: return
        saveJob?.cancel()
        _open.value = null
        openId.value = null
        viewModelScope.launch {
            persist(d)
            repo.trash(d.project.id)
        }
    }

    /**
     * Starts a new garment from a finished one: linked designs are copied (so the original stays
     * as made), sketches and materials carry over, and the tweaks are written into its notes.
     */
    fun spinOff(name: String, tweaks: String) {
        val d = _open.value ?: return
        leaveCurrent()
        viewModelScope.launch {
            persist(d)
            val links = d.links.map { link ->
                if (link.kind != LinkKind.DESIGN) return@map link
                val original = designs.get(link.id) ?: return@map link
                val copyId = designs.create()
                designs.update(copyId) {
                    it.copy(name = "${original.name.ifBlank { "Design" }} (${name.ifBlank { "spin-off" }})", shapes = original.shapes)
                }
                ProjectLink(LinkKind.DESIGN, copyId)
            }
            val date = DateTimeFormatter.ofPattern("d MMM yyyy").format(Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault()))
            val from = d.project.name.ifBlank { "an earlier project" }
            val notes = buildString {
                append("Spun off from $from on $date.")
                if (tweaks.isNotBlank()) append("\n\nTweaks:\n").append(tweaks.trim())
            }
            val id = repo.create(
                Project(
                    name = name.ifBlank { "${d.project.name} v2".trim() },
                    notes = notes,
                    materials = ProjectJson.encodeMaterials(d.materials),
                    links = ProjectJson.encodeLinks(links),
                    parentId = d.project.id,
                )
            )
            load(id)
        }
    }

    fun flush() {
        val d = _open.value ?: return
        saveJob?.cancel()
        viewModelScope.launch { persist(d) }
    }

    private fun edit(now: Boolean = false, change: (ProjectDraft) -> ProjectDraft) {
        val d = _open.value ?: return
        val next = change(d)
        if (next == d) return
        _open.value = next
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (!now) delay(500)
            persist(next)
        }
    }

    private suspend fun persist(d: ProjectDraft) {
        repo.update(d.project.id) {
            it.copy(
                name = d.project.name,
                step = d.project.step,
                cover = d.project.cover,
                notes = d.project.notes,
                todos = ProjectJson.encodeTodos(d.todos),
                materials = ProjectJson.encodeMaterials(d.materials),
                links = ProjectJson.encodeLinks(d.links),
            )
        }
    }

    /** Saves the open project, or deletes it if nothing was added. */
    private fun leaveCurrent() {
        val d = _open.value ?: return
        saveJob?.cancel()
        val blank = d.project.name.isBlank() && d.project.notes.isBlank() && d.todos.isEmpty() &&
            d.materials.isEmpty() && d.links.isEmpty() && photos.value.isEmpty() && d.project.parentId == null
        viewModelScope.launch { if (blank) repo.delete(d.project.id) else persist(d) }
    }

    private suspend fun load(id: Long) {
        val p = repo.get(id) ?: return
        openId.value = id
        _open.value = ProjectDraft(p)
    }

    companion object {
        fun factory(repo: ProjectRepository, designs: DesignRepository, ideas: IdeaRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { ProjectsViewModel(repo, designs, ideas) } }
    }
}
