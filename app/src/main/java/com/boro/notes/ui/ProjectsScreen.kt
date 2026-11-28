package com.boro.notes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boro.notes.data.Design
import com.boro.notes.data.MaterialKind
import com.boro.notes.data.turned
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.boro.notes.data.Idea
import com.boro.notes.data.IdeaKind
import com.boro.notes.data.LinkKind
import com.boro.notes.data.Material
import com.boro.notes.data.PhotoMark
import com.boro.notes.data.PhotoPin
import com.boro.notes.data.Project
import com.boro.notes.data.ProjectJson
import com.boro.notes.data.ProjectLink
import com.boro.notes.data.ProjectPhoto
import com.boro.notes.data.ProjectStep
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

const val TAG_PROJECTS = "projectsList"
const val TAG_NEW_PROJECT = "newProject"
const val TAG_PROJECT_PREFIX = "project-"
const val TAG_PROJECT_EDITOR = "projectEditor"
const val TAG_PHOTO_VIEWER = "photoViewer"

private val MARK_COLOURS = listOf(0xFFD32F2F, 0xFFFFD54F, 0xFFFFFFFF, 0xFF2E4A7D, 0xFF2B2B2B)
private val MARK_SIZES = listOf("Fine" to 2f, "Medium" to 4f, "Bold" to 8f)

internal fun photoDate(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** The Projects tab: what's on the table (with its step), and a shelf of finished pieces. */
@Composable
fun ProjectsList(vm: ProjectsViewModel, modifier: Modifier = Modifier) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val latest by vm.latestPhotos.collectAsStateWithLifecycle()
    var finished by rememberSaveable { mutableStateOf(false) }
    val active = projects.filter { it.step != ProjectStep.FINISHED }
    val done = projects.filter { it.step == ProjectStep.FINISHED }
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().testTag(TAG_PROJECTS)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !finished, onClick = { finished = false }, label = { Text("On the table ${active.size}") })
                FilterChip(
                    selected = finished, onClick = { finished = true },
                    leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) },
                    label = { Text("Finished ${done.size}") }, modifier = Modifier.testTag("projects-finished"),
                )
            }
            val list = if (finished) done else active
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Inventory2, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(if (finished) "Finished pieces land here" else "Nothing on the table", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (finished) "Move a project to Finished to shelve it. You can spin a new garment off it later."
                            else "Tap + to start a project, then add photos as it comes together.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (finished) 140.dp else 170.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { p ->
                        ProjectCard(p, p.cover ?: latest[p.id], finished) { vm.openProject(p.id) }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = vm::create,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).testTag(TAG_NEW_PROJECT),
        ) { Icon(Icons.Filled.Add, "New project") }
    }
}

@Composable
private fun ProjectCard(p: Project, cover: String?, shelf: Boolean, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceContainer)
            .clickable(onClick = onOpen)
            .testTag("$TAG_PROJECT_PREFIX${p.id}"),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (shelf) 1f else 4f / 3f).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
            if (cover != null) StoredImage(cover, Modifier.fillMaxSize())
            else Icon(Icons.Filled.Checkroom, null, tint = cs.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(p.name.ifBlank { "Untitled project" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!shelf) {
                Text(p.step.label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                StepBar(p.step)
            }
        }
    }
}

/** Six little stitches, filled up to the current step. */
@Composable
private fun StepBar(step: ProjectStep) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ProjectStep.entries.forEach { s ->
            Box(
                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (s.ordinal <= step.ordinal) cs.primary else cs.outlineVariant)
            )
        }
    }
}

/** An open project: cover, steps, dated photos, to-dos, materials, links and notes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectEditor(
    draft: ProjectDraft,
    vm: ProjectsViewModel,
    onOpenDesign: (Long) -> Unit,
    onOpenIdea: (Long) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val p = draft.project
    val photos by vm.photos.collectAsStateWithLifecycle()
    val designs by vm.allDesigns.collectAsStateWithLifecycle()
    val ideas by vm.allIdeas.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var spinning by remember { mutableStateOf(false) }
    var editingMaterial by remember { mutableStateOf<Pair<Int?, Material>?>(null) }
    var linking by remember { mutableStateOf(false) }
    var newTodo by remember { mutableStateOf("") }
    val source = rememberPhotoSource(vm::addPhoto)
    val uri = LocalUriHandler.current
    val cover = p.cover ?: photos.firstOrNull()?.file

    Surface(Modifier.fillMaxSize().testTag(TAG_PROJECT_EDITOR), color = cs.surface) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::close, modifier = Modifier.testTag(TAG_BACK)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Box(Modifier.weight(1f)) {
                    PlainField(p.name, vm::rename, "Name this project", MaterialTheme.typography.titleLarge, Modifier.testTag("project-name"))
                }
                Box {
                    var open by remember { mutableStateOf(false) }
                    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text("Spin off a new garment") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.CallSplit, null) },
                            onClick = { open = false; spinning = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete project") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { open = false; confirmDelete = true },
                        )
                    }
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Cover: always a square, so tall garments like jeans fit. On a wide screen the step
                // and stats sit beside it; on a narrow one they follow underneath.
                item {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val wide = maxWidth >= 560.dp
                        val coverWidth = minOf(maxWidth * 0.45f, 420.dp)
                        val coverBox: @Composable (Modifier) -> Unit = { m ->
                            Box(
                                m.aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(cs.surfaceVariant)
                                    .clickable(enabled = cover != null) { photos.firstOrNull { it.file == cover }?.let { viewing = it.id } }
                                    .testTag("project-cover"),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (cover != null) StoredImage(cover, Modifier.fillMaxSize())
                                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextButton(onClick = source.camera) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Take a photo") }
                                    TextButton(onClick = source.gallery) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Gallery") }
                                }
                            }
                        }
                        val details: @Composable () -> Unit = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                p.parentId?.let { parent ->
                                    val from = projects.firstOrNull { it.id == parent }
                                    if (from != null) {
                                        Text(
                                            "Spun off from ${from.name.ifBlank { "a project" }}",
                                            style = MaterialTheme.typography.labelMedium, color = cs.primary,
                                            modifier = Modifier.clickable { vm.openProject(parent) },
                                        )
                                    }
                                }
                                SectionTitle("Step")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ProjectStep.entries.forEach { s ->
                                        val past = s.ordinal < p.step.ordinal
                                        FilterChip(
                                            selected = s == p.step,
                                            onClick = { vm.setStep(s) },
                                            leadingIcon = if (past) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
                                            label = { Text(s.label) },
                                            modifier = Modifier.testTag("step-${s.name}"),
                                        )
                                    }
                                }
                                val doneTodos = draft.todos.count { it.done }
                                Text(
                                    listOf(
                                        "${photos.size} photo${if (photos.size == 1) "" else "s"}",
                                        if (draft.todos.isEmpty()) null else "$doneTodos of ${draft.todos.size} to-dos done",
                                        if (draft.materials.isEmpty()) null else "${draft.materials.size} material${if (draft.materials.size == 1) "" else "s"}",
                                    ).filterNotNull().joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                    modifier = Modifier.testTag("project-stats"),
                                )
                                if (p.step == ProjectStep.FINISHED) {
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.tertiaryContainer)
                                            .clickable { spinning = true }.padding(12.dp).testTag("spin-off"),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.CallSplit, null, tint = cs.onTertiaryContainer)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Spin off a new garment", style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer)
                                            Text("Copies its designs and materials, and notes your tweaks", style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                coverBox(Modifier.width(coverWidth))
                                Box(Modifier.weight(1f)) { details() }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                coverBox(Modifier.fillMaxWidth())
                                details()
                            }
                        }
                    }
                }
                // Photos, newest first.
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Progress photos", Modifier.weight(1f))
                        IconButton(onClick = source.camera, modifier = Modifier.testTag("project-camera")) { Icon(Icons.Filled.PhotoCamera, "Take a photo") }
                        IconButton(onClick = source.gallery, modifier = Modifier.testTag("project-gallery")) { Icon(Icons.Filled.PhotoLibrary, "Add from gallery") }
                    }
                    if (photos.isEmpty()) {
                        Text("Photos you add are dated, so you can look back at how it came together.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photos, key = { it.id }) { photo ->
                                Column(Modifier.width(112.dp).clickable { viewing = photo.id }.testTag("photo-${photo.id}")) {
                                    Box {
                                        StoredImage(photo.file, Modifier.size(112.dp).clip(RoundedCornerShape(10.dp)))
                                        val pins = remember(photo.pins) { ProjectJson.pins(photo.pins).size }
                                        if (pins > 0) {
                                            Row(
                                                Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(Icons.Filled.PushPin, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                Text("$pins", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    Text(photoDate(photo.takenAt), style = MaterialTheme.typography.labelMedium)
                                    if (photo.caption.isNotBlank()) {
                                        Text(photo.caption, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                // To-dos.
                item {
                    SectionTitle("To do")
                    draft.todos.forEachIndexed { i, t ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { vm.toggleTodo(i) }, modifier = Modifier.testTag("todo-check-$i")) {
                                Icon(if (t.done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, if (t.done) "Done" else "Not done", tint = if (t.done) cs.primary else cs.onSurfaceVariant)
                            }
                            Text(
                                t.text, modifier = Modifier.weight(1f),
                                color = if (t.done) cs.onSurfaceVariant else cs.onSurface,
                                textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            )
                            IconButton(onClick = { vm.removeTodo(i) }) { Icon(Icons.Filled.Close, "Remove", tint = cs.onSurfaceVariant) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).padding(start = 12.dp)) {
                            PlainField(newTodo, { newTodo = it }, "Add a step, like “buy copper rivets”", MaterialTheme.typography.bodyLarge, Modifier.testTag("todo-field"))
                        }
                        IconButton(onClick = { vm.addTodo(newTodo); newTodo = "" }, enabled = newTodo.isNotBlank(), modifier = Modifier.testTag("todo-add")) {
                            Icon(Icons.Filled.Add, "Add to-do")
                        }
                    }
                }
                // Materials.
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Materials", Modifier.weight(1f))
                        IconButton(onClick = { editingMaterial = null to Material() }, modifier = Modifier.testTag("material-add")) { Icon(Icons.Filled.Add, "Add material") }
                    }
                    if (draft.materials.isEmpty()) {
                        Text("Fabric (weight, weave), hardware like rivets and buttons, and notions like thread: with links and how many.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    val sorted = draft.materials.withIndex().sortedBy { MaterialKind.of(it.value.kind).ordinal }
                    sorted.forEach { (i, m) ->
                        val kind = MaterialKind.of(m.kind)
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer)
                                .clickable { editingMaterial = i to m }.padding(12.dp).testTag("material-$i"),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.name.ifBlank { kind.label }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(kind.label, style = MaterialTheme.typography.labelSmall, color = cs.onSecondaryContainer,
                                    modifier = Modifier.background(cs.secondaryContainer, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                            val details = when (kind) {
                                MaterialKind.FABRIC -> listOf(m.weight, m.weave, m.colour, m.quantity)
                                else -> listOf(if (m.quantity.isBlank()) "" else "× ${m.quantity}", m.colour)
                            }.filter { it.isNotBlank() }
                            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            if (m.notes.isNotBlank()) Text(m.notes, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            if (m.link.isNotBlank()) {
                                Row(
                                    Modifier.padding(top = 4.dp).clickable { runCatching { uri.openUri(m.link.let { if (it.startsWith("http")) it else "https://$it" }) } },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = cs.primary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(m.link, color = cs.primary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                // Links to designs and sketches.
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Designs and sketches", Modifier.weight(1f))
                        IconButton(onClick = { linking = true }, modifier = Modifier.testTag("link-add")) { Icon(Icons.Filled.AddLink, "Link a design or sketch") }
                    }
                    if (draft.links.isEmpty()) {
                        Text("Link the design, sketches or collages this came from.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        draft.links.forEach { link ->
                            when (link.kind) {
                                LinkKind.DESIGN -> designs.firstOrNull { it.id == link.id }?.let { d ->
                                    LinkCard(d.name.ifBlank { "Untitled design" }, Icons.Filled.Checkroom, "link-design-${d.id}", { onOpenDesign(d.id) }) {
                                        DesignThumbnail(d, Modifier.fillMaxSize(), ink = Color(0xFF2B2B2B), background = Color(0xFFFBF8F1))
                                    }
                                }
                                LinkKind.IDEA -> ideas.firstOrNull { it.id == link.id }?.let { idea ->
                                    LinkCard(
                                        idea.title.ifBlank { if (idea.kind == IdeaKind.SKETCH) "Sketch" else "Collage" },
                                        if (idea.kind == IdeaKind.SKETCH) Icons.Filled.Brush else Icons.Filled.Dashboard,
                                        "link-idea-${idea.id}", { onOpenIdea(idea.id) },
                                    ) { IdeaThumbnail(idea, ideas, designs, Modifier.fillMaxSize()) }
                                }
                            }
                        }
                    }
                }
                // Free notes.
                item {
                    SectionTitle("Notes")
                    PlainField(p.notes, vm::setNotes, "Fit, changes, what to try next time…", MaterialTheme.typography.bodyLarge, Modifier.testTag("project-notes"), singleLine = false)
                }
            }
        }
    }

    photos.firstOrNull { it.id == viewing }?.let { photo ->
        PhotoViewer(
            photo, isCover = cover == photo.file,
            onSave = { caption, pins, marks, file -> vm.savePhoto(photo.id, caption, pins, marks, file, photo.file) },
            onCover = { vm.setCover(photo.file) },
            onDelete = { vm.deletePhoto(photo); viewing = null },
            onClose = { viewing = null },
        )
    }
    if (confirmDelete) ConfirmDeleteDialog("this project", onConfirm = vm::deleteOpen, onDismiss = { confirmDelete = false })
    if (spinning) SpinOffDialog(p.name, onDone = vm::spinOff, onDismiss = { spinning = false })
    editingMaterial?.let { (index, m) ->
        MaterialDialog(
            m, isNew = index == null,
            onSave = { vm.saveMaterial(index, it) },
            onDelete = { index?.let(vm::removeMaterial) },
            onDismiss = { editingMaterial = null },
        )
    }
    if (linking) {
        val picked = draft.links.toSet()
        AlertDialog(
            onDismissRequest = { linking = false },
            title = { Text("Link designs and sketches") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (designs.isEmpty() && ideas.isEmpty()) item { Text("No designs or sketches yet.") }
                    if (designs.isNotEmpty()) item { MenuLabel("Designs") }
                    items(designs, key = { "d${it.id}" }) { d ->
                        LinkRow(d.name.ifBlank { "Untitled design" }, ProjectLink(LinkKind.DESIGN, d.id) in picked, "pick-design-${d.id}") {
                            vm.toggleLink(ProjectLink(LinkKind.DESIGN, d.id))
                        }
                    }
                    if (ideas.isNotEmpty()) item { MenuLabel("Sketches and collages") }
                    items(ideas, key = { "i${it.id}" }) { idea ->
                        LinkRow(idea.title.ifBlank { if (idea.kind == IdeaKind.SKETCH) "Sketch" else "Collage" }, ProjectLink(LinkKind.IDEA, idea.id) in picked, "pick-idea-${idea.id}") {
                            vm.toggleLink(ProjectLink(LinkKind.IDEA, idea.id))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linking = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(bottom = 4.dp))
}

@Composable
private fun LinkRow(name: String, on: Boolean, tag: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle).padding(10.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null, tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LinkCard(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onOpen: () -> Unit, thumb: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(104.dp).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainer).clickable(onClick = onOpen).testTag(tag)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(Color(0xFFFBF8F1))) { thumb() }
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SpinOffDialog(from: String, onDone: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("${from.ifBlank { "Project" }} v2") }
    var tweaks by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.CallSplit, null) },
        title = { Text("Spin off a new garment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("spin-name"))
                OutlinedTextField(
                    tweaks, { tweaks = it }, label = { Text("Tweaks for this one") },
                    placeholder = { Text("Wider leg, deeper scoop pocket, quilted knee…") },
                    minLines = 3, modifier = Modifier.fillMaxWidth().testTag("spin-tweaks"),
                )
                Text("Its designs are copied so the original stays as made. Materials and sketches carry over.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onDone(name, tweaks) }, modifier = Modifier.testTag("spin-ok")) { Text("Spin off") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialDialog(initial: Material, isNew: Boolean, onSave: (Material) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var m by remember { mutableStateOf(initial.copy(kind = MaterialKind.of(initial.kind).label)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add material" else "Material") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val kind = MaterialKind.of(m.kind)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MaterialKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { m = m.copy(kind = k.label) }, label = { Text(k.label) }, modifier = Modifier.testTag("material-kind-${k.name}"))
                    }
                }
                val namePlaceholder = when (kind) {
                    MaterialKind.FABRIC -> "14 oz selvedge denim"
                    MaterialKind.HARDWARE -> "Copper rivets"
                    MaterialKind.NOTIONS -> "Tex 70 thread"
                }
                OutlinedTextField(m.name, { m = m.copy(name = it) }, label = { Text("Name") }, placeholder = { Text(namePlaceholder) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("material-name"))
                when (kind) {
                    MaterialKind.FABRIC -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(m.weight, { m = m.copy(weight = it) }, label = { Text("Weight") }, placeholder = { Text("14 oz") }, singleLine = true, modifier = Modifier.weight(1f).testTag("material-weight"))
                            OutlinedTextField(m.weave, { m = m.copy(weave = it) }, label = { Text("Weave") }, placeholder = { Text("3x1 twill") }, singleLine = true, modifier = Modifier.weight(1f).testTag("material-weave"))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(m.colour, { m = m.copy(colour = it) }, label = { Text("Colour") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(m.quantity, { m = m.copy(quantity = it) }, label = { Text("Amount") }, placeholder = { Text("2.5 yd") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    MaterialKind.HARDWARE, MaterialKind.NOTIONS -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                m.quantity, { m = m.copy(quantity = it) }, label = { Text("Quantity") },
                                placeholder = { Text(if (kind == MaterialKind.HARDWARE) "12" else "2 spools") },
                                singleLine = true, modifier = Modifier.weight(1f).testTag("material-quantity"),
                            )
                            OutlinedTextField(
                                m.colour, { m = m.copy(colour = it) },
                                label = { Text(if (kind == MaterialKind.HARDWARE) "Finish" else "Colour") },
                                placeholder = { Text(if (kind == MaterialKind.HARDWARE) "Antique copper" else "Mustard") },
                                singleLine = true, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                OutlinedTextField(m.link, { m = m.copy(link = it) }, label = { Text("Product link") }, placeholder = { Text("https://") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("material-link"))
                OutlinedTextField(m.notes, { m = m.copy(notes = it) }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onSave(m) }, modifier = Modifier.testTag("material-save")) { Text("Save") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = { onDismiss(); onDelete() }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private enum class PhotoMode { VIEW, PIN, MARK }

/**
 * A progress photo, full screen. Pin mode drops a note where you tap; Mark mode circles and
 * scribbles with a finger. Changes save when it closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoViewer(
    photo: ProjectPhoto,
    isCover: Boolean,
    onSave: (caption: String, pins: List<PhotoPin>, marks: List<PhotoMark>, file: String) -> Unit,
    onCover: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var caption by remember(photo.id) { mutableStateOf(photo.caption) }
    val pins = remember(photo.id) { mutableStateListOf<PhotoPin>().apply { addAll(ProjectJson.pins(photo.pins)) } }
    val marks = remember(photo.id) { mutableStateListOf<PhotoMark>().apply { addAll(ProjectJson.marks(photo.marks)) } }
    var mode by remember { mutableStateOf(PhotoMode.VIEW) }
    var markColour by remember { mutableStateOf(MARK_COLOURS[0]) }
    var markWidth by remember { mutableStateOf(4f) }
    var openPin by remember { mutableStateOf<Int?>(null) }
    var newPinAt by remember { mutableStateOf<Offset?>(null) }
    var editPin by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val live = remember { mutableStateListOf<Offset>() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Turning saves a new copy; the copies in between (and the original, once replaced) are tidied on close.
    var file by remember(photo.id) { mutableStateOf(photo.file) }
    val made = remember(photo.id) { mutableListOf<String>() }
    var turning by remember { mutableStateOf(false) }
    val bitmap = rememberStoredImage(file)
    fun turn(clockwise: Boolean) {
        if (turning) return
        turning = true
        scope.launch {
            val out = rotateStoredImage(context, file, clockwise)
            if (out != null) {
                made += out
                file = out
                val p2 = pins.map { it.turned(clockwise) }
                pins.clear(); pins.addAll(p2)
                val m2 = marks.map { it.turned(clockwise) }
                marks.clear(); marks.addAll(m2)
            }
            turning = false
        }
    }
    val close = {
        onSave(caption, pins.toList(), marks.toList(), file)
        made.filter { it != file }.forEach { deleteStoredImage(context, it) }
        if (file != photo.file) deleteStoredImage(context, photo.file)
        onClose()
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag(TAG_PHOTO_VIEWER), color = Color(0xFF151515), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close, modifier = Modifier.testTag("photo-close")) { Icon(Icons.Filled.Close, "Close") }
                    Text(photoDate(photo.takenAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCover, enabled = !isCover) { Text(if (isCover) "Cover" else "Make cover", color = if (isCover) Color.White.copy(alpha = 0.5f) else Color.White) }
                    IconButton(onClick = { turn(false) }, enabled = bitmap != null, modifier = Modifier.testTag("photo-turn-left")) {
                        Icon(Icons.Filled.Rotate90DegreesCcw, "Turn left")
                    }
                    IconButton(onClick = { turn(true) }, enabled = bitmap != null, modifier = Modifier.testTag("photo-turn-right")) {
                        Icon(Icons.Filled.Rotate90DegreesCcw, "Turn right", Modifier.graphicsLayer(scaleX = -1f))
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete photo") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val dens = LocalDensity.current
                    val aspect = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1f
                    val boxW = with(dens) { maxWidth.toPx() }
                    val boxH = with(dens) { maxHeight.toPx() }
                    val w = minOf(boxW, boxH * aspect)
                    val h = w / aspect
                    Box(Modifier.size(with(dens) { w.toDp() }, with(dens) { h.toDp() })) {
                        if (bitmap != null) Image(bitmap, caption.ifBlank { "Photo" }, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        Canvas(
                            Modifier.fillMaxSize().testTag("photo-canvas").pointerInput(mode, markColour, markWidth, w, h) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val start = down.position
                                    live.clear()
                                    if (mode == PhotoMode.MARK) live.add(start)
                                    var moved = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!c.pressed) break
                                        if ((c.position - start).getDistance() > 12f) moved = true
                                        if (mode == PhotoMode.MARK) { live.add(c.position); c.consume() }
                                    }
                                    val pts = live.toList()
                                    live.clear()
                                    when (mode) {
                                        PhotoMode.MARK -> if (pts.size > 1) marks.add(PhotoMark(markColour, markWidth, pts.flatMap { listOf(it.x / w, it.y / h) }))
                                        PhotoMode.PIN -> if (!moved) newPinAt = Offset(start.x / w, start.y / h)
                                        PhotoMode.VIEW -> if (!moved) openPin = null
                                    }
                                }
                            },
                        ) {
                            marks.forEach { m -> drawMark(m.points.chunked(2).mapNotNull { if (it.size == 2) Offset(it[0] * size.width, it[1] * size.height) else null }, Color(m.color), m.width * density) }
                            if (live.isNotEmpty()) drawMark(live, Color(markColour), markWidth * density)
                        }
                        // Pins sit over the photo; tap one to read it.
                        pins.forEachIndexed { i, pin ->
                            Box(
                                Modifier
                                    .offset { IntOffset((pin.x * w).roundToInt() - 14.dp.roundToPx(), (pin.y * h).roundToInt() - 14.dp.roundToPx()) }
                                    .size(28.dp)
                                    .background(if (openPin == i) cs.primary else Color(0xFFD32F2F), CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                                    .clickable { openPin = if (openPin == i) null else i }
                                    .testTag("pin-$i"),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
                openPin?.let { i ->
                    pins.getOrNull(i)?.let { pin ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}. ${pin.text}", modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                            TextButton(onClick = { editPin = i }) { Text("Edit", color = Color.White) }
                            IconButton(onClick = { pins.removeAt(i); openPin = null }) { Icon(Icons.Filled.Delete, "Remove pin") }
                        }
                    }
                }
                PlainField(
                    caption, { caption = it }, "Add a caption",
                    MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    Modifier.padding(horizontal = 16.dp).testTag("photo-caption"),
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToolGroup(Color.White.copy(alpha = 0.12f), Color.White) {
                        ToolButton(Icons.Filled.PanTool, "Look", "photo-view", selected = mode == PhotoMode.VIEW) { mode = PhotoMode.VIEW }
                        ToolButton(Icons.Filled.PushPin, "Pin a note", "photo-pin", selected = mode == PhotoMode.PIN) { mode = PhotoMode.PIN }
                        ToolPopup(Icons.Filled.Draw, "Mark up", "photo-mark", selected = mode == PhotoMode.MARK, accent = Color(markColour), onSelect = { mode = PhotoMode.MARK }) { _ ->
                            PopupSection("Colour") { ColourDots(MARK_COLOURS, markColour, "mark-colour") { markColour = it } }
                            PopupSection("Size") { SizeDots(MARK_SIZES, markWidth, Color(markColour), "mark-size") { markWidth = it } }
                            ActionRow(Icons.Filled.Delete, "Clear all marks", "mark-clear", enabled = marks.isNotEmpty()) { marks.clear() }
                        }
                    }
                    if (mode == PhotoMode.MARK) {
                        ToolGroup(Color.White.copy(alpha = 0.12f), Color.White) {
                            ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo mark", "mark-undo", enabled = marks.isNotEmpty()) { marks.removeAt(marks.lastIndex) }
                        }
                    }
                }
                Text(
                    when (mode) {
                        PhotoMode.VIEW -> if (pins.isEmpty()) "Pin notes to spots, or mark it up with a finger" else "Tap a pin to read it"
                        PhotoMode.PIN -> "Tap where the note goes"
                        PhotoMode.MARK -> "Draw with one finger"
                    },
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                )
            }
        }
        newPinAt?.let { at ->
            TextPrompt("Pin a note", "Redo this rivet, seam puckers here…", "", singleLine = false,
                onDone = { pins.add(PhotoPin(at.x, at.y, it)); openPin = pins.lastIndex },
                onDismiss = { newPinAt = null })
        }
        editPin?.let { i ->
            TextPrompt("Edit note", "", pins.getOrNull(i)?.text.orEmpty(), singleLine = false,
                onDone = { t -> if (i in pins.indices) pins[i] = pins[i].copy(text = t) },
                onDismiss = { editPin = null })
        }
        if (confirmDelete) {
            ConfirmDeleteDialog("this photo", toTrash = false, onConfirm = onDelete, onDismiss = { confirmDelete = false })
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMark(pts: List<Offset>, color: Color, width: Float) {
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size - 1) {
            val mid = Offset((pts[i].x + pts[i + 1].x) / 2, (pts[i].y + pts[i + 1].y) / 2)
            quadraticTo(pts[i].x, pts[i].y, mid.x, mid.y)
        }
        lineTo(pts.last().x, pts.last().y)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
