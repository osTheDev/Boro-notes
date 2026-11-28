package com.boro.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boro.notes.data.Design
import com.boro.notes.data.Stage
import com.boro.notes.sketch.Edits
import com.boro.notes.sketch.FillPattern
import com.boro.notes.sketch.Preset
import com.boro.notes.sketch.SketchCodec
import com.boro.notes.sketch.Stamp

const val TAG_DESIGN_LIST = "designList"
const val TAG_NEW_DESIGN = "newDesign"
const val TAG_SKETCH_EDITOR = "sketchEditor"
const val TAG_DESIGN_PREFIX = "design-"
const val TAG_LINKED_NOTE = "linkedNote"
const val TAG_LINK_NOTE_BUTTON = "linkNote"
const val TAG_NOTES_TOGGLE = "notesToggle"
const val TAG_PRESET_PREFIX = "preset-"

/** Tools that drag out a perfect shape and preview it while dragging. */
private val SHAPE_TOOLS = setOf(Tool.LINE, Tool.BOX, Tool.OVAL)

/** The four tabs of the Designs section. */
enum class StudioTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DESIGNS("Designs", Icons.Filled.Checkroom),
    PATTERN("Pattern", Icons.Filled.ContentCut),
    SKETCH("Sketch", Icons.Filled.Brush),
    PROJECTS("Projects", Icons.Filled.Inventory2),
}

/** True while something in the Designs section is open full screen (so the cover screen hides its bar). */
@Composable
fun designsEditorOpen(designsVm: DesignsViewModel, projectsVm: ProjectsViewModel, ideasVm: IdeasViewModel): Boolean =
    designsVm.sketch.collectAsStateWithLifecycle().value != null ||
        projectsVm.open.collectAsStateWithLifecycle().value != null ||
        ideasVm.open.collectAsStateWithLifecycle().value != null

/**
 * Designs: preset garments to edit, pattern pieces (coming), quick sketches and collages, and the
 * projects on the table. Something opened from a project (a linked design or sketch) sits over it,
 * so Back returns to the project.
 */
@Composable
fun DesignsScreen(
    designsVm: DesignsViewModel,
    notesVm: NotesViewModel,
    wide: Boolean,
    projectsVm: ProjectsViewModel,
    ideasVm: IdeasViewModel,
) {
    val sketch by designsVm.sketch.collectAsStateWithLifecycle()
    val project by projectsVm.open.collectAsStateWithLifecycle()
    val idea by ideasVm.open.collectAsStateWithLifecycle()
    val designs by designsVm.all.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(StudioTab.DESIGNS) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        designsVm.flush()
        projectsVm.flush()
        ideasVm.flush()
    }

    val currentSketch = sketch
    val currentIdea = idea
    val currentProject = project
    when {
        currentSketch != null -> {
            BackHandler { designsVm.close() }
            SketchEditor(currentSketch, designsVm, notesVm, wide)
        }
        currentIdea != null -> {
            BackHandler { ideasVm.close() }
            IdeaEditor(currentIdea, ideasVm, designs)
        }
        currentProject != null -> {
            BackHandler { projectsVm.close() }
            ProjectEditor(
                currentProject, projectsVm,
                onOpenDesign = designsVm::open,
                onOpenIdea = ideasVm::openIdea,
            )
        }
        else -> Column(Modifier.fillMaxSize()) {
            StudioTabs(tab) { tab = it }
            when (tab) {
                StudioTab.DESIGNS -> DesignGrid(designsVm, Modifier.weight(1f))
                StudioTab.PATTERN -> PatternComingSoon(Modifier.weight(1f))
                StudioTab.SKETCH -> SketchBoard(ideasVm, designs, Modifier.weight(1f))
                StudioTab.PROJECTS -> ProjectsList(projectsVm, Modifier.weight(1f))
            }
        }
    }
}

/** Icon tabs across the top, each with its own colour when picked. */
@Composable
private fun StudioTabs(tab: StudioTab, onPick: (StudioTab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tints = listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.primary to cs.onPrimary)
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StudioTab.entries.forEachIndexed { i, t ->
            val on = t == tab
            val (bg, fg) = tints[i]
            Row(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) bg else cs.surfaceContainer)
                    .clickable { onPick(t) }
                    .testTag("studio-tab-${t.name}"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon, null, tint = if (on) fg else cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    t.label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (on) fg else cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PatternComingSoon(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().padding(32.dp).testTag("patternSoon"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.ContentCut, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(8.dp))
            Text("Pattern pieces are coming", style = MaterialTheme.typography.titleSmall)
            Text(
                "Flat pieces at true size, with seam allowance, grainline, notches and cut labels.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
    }
}

/** Designs as a grid of thumbnails, newest first. */
@Composable
private fun DesignGrid(vm: DesignsViewModel, modifier: Modifier) {
    val all by vm.all.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().testTag(TAG_DESIGN_LIST)) {
        if (all.isEmpty()) {
            EmptyDesigns(Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(all, key = { it.id }) { design ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.surfaceContainer)
                            .clickable { vm.open(design.id) }
                            .testTag("$TAG_DESIGN_PREFIX${design.id}"),
                    ) {
                        DesignThumbnail(design, Modifier.fillMaxWidth().aspectRatio(0.75f))
                        Text(
                            design.name.ifBlank { "Untitled design" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        NewDesignFab(vm, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp))
    }
}

@Composable
private fun NewDesignFab(vm: DesignsViewModel, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    FloatingActionButton(onClick = { picking = true }, modifier = modifier.testTag(TAG_NEW_DESIGN)) {
        Icon(Icons.Filled.Add, contentDescription = "New design")
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Start from") },
            text = {
                Column {
                    Preset.entries.forEach { preset ->
                        TextButton(
                            onClick = { picking = false; vm.createDesign(preset) },
                            modifier = Modifier.fillMaxWidth().testTag("$TAG_PRESET_PREFIX${preset.name}"),
                        ) { Text(preset.label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyDesigns(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("No designs yet. Tap + to start from a garment.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DesignThumbnail(
    design: Design,
    modifier: Modifier,
    ink: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surface,
) {
    val shapes = remember(design.shapes) { SketchCodec.decode(design.shapes) }
    Canvas(modifier.background(background).padding(8.dp)) { drawThumbnail(shapes, ink) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SketchEditor(state: SketchState, vm: DesignsViewModel, notesVm: NotesViewModel, wide: Boolean) {
    // On the cover screen the linked note opens over the canvas; on the inner screen it sits beside it.
    var showNote by rememberSaveable(state.id) { mutableStateOf(false) }
    val noteEditor by notesVm.editor.collectAsStateWithLifecycle()

    LaunchedEffect(state.noteId) { state.noteId?.let(notesVm::open) }

    if (!wide && showNote) {
        BackHandler { showNote = false }
        LinkedNotePane(state, noteEditor, vm, notesVm, onBack = { showNote = false }, Modifier.fillMaxSize())
        return
    }

    Scaffold(
        modifier = Modifier.testTag(TAG_SKETCH_EDITOR),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = vm::close) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextField(
                        value = state.name,
                        onValueChange = vm::rename,
                        placeholder = { Text("Design name") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                actions = {
                    if (!wide) {
                        TextButton(onClick = {
                            if (state.noteId == null) vm.createLinkedNote()
                            showNote = true
                        }) { Text("Note") }
                    } else {
                        // Inner screen: the design's note opens full screen.
                        TextButton(
                            onClick = {
                                if (state.noteId == null) vm.createLinkedNote()
                                notesVm.setFullScreen(true)
                            },
                            modifier = Modifier.testTag(TAG_NOTES_TOGGLE),
                        ) { Text("Notes") }
                    }
                    EditorOverflow(vm)
                },
            )
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                SketchCanvas(
                    shapes = state.shapes,
                    onStroke = vm::addStroke,
                    onTap = vm::tap,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    previewShape = if (state.tool in SHAPE_TOOLS) vm::shapeFor else null,
                    editing = state.selectedShape?.let { state.shapes.getOrNull(it) }.takeIf { state.tool == Tool.POINTS },
                    selectedPoint = state.selectedPoint,
                    selection = state.selection,
                    onDragStart = if (state.tool == Tool.POINTS || state.tool == Tool.SELECT) vm::beginDrag else null,
                    onDrag = vm::drag,
                    onDragEnd = vm::endDrag,
                )
                HorizontalDivider()
                ToolBar(state, vm)
            }
        }
    }
}

@Composable
private fun LinkedNotePane(
    state: SketchState,
    noteEditor: EditorState?,
    vm: DesignsViewModel,
    notesVm: NotesViewModel,
    onBack: (() -> Unit)?,
    modifier: Modifier,
) {
    val note = noteEditor?.takeIf { it.id == state.noteId }
    Box(modifier.testTag(TAG_LINKED_NOTE)) {
        if (note != null) {
            EditorPane(
                note,
                notesVm,
                onBack = onBack,
                showDelete = false,
                // Inner screen: blow the note up to the whole screen.
                onExpand = if (onBack == null) ({ notesVm.setFullScreen(true) }) else null,
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (state.noteId == null) "Keep fabric, measurements and ideas for this design in a linked note."
                    else "Opening note…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                if (state.noteId == null) {
                    Button(onClick = vm::createLinkedNote, modifier = Modifier.testTag(TAG_LINK_NOTE_BUTTON)) {
                        Text("Add a linked note")
                    }
                }
                if (onBack != null) {
                    TextButton(onClick = onBack) { Text("Back to sketch") }
                }
            }
        }
    }
}

@Composable
private fun EditorOverflow(vm: DesignsViewModel) {
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) ConfirmDeleteDialog("this design", onConfirm = vm::deleteOpenDesign, onDismiss = { confirmDelete = false })
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Clear canvas") }, onClick = { vm.clearCanvas(); open = false })
            DropdownMenuItem(text = { Text("Unlink note") }, onClick = { vm.unlinkNote(); open = false })
            DropdownMenuItem(
                text = { Text("Delete design") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = { open = false; confirmDelete = true },
            )
        }
    }
}

private data class ToolGroup(val label: String, val tools: List<Tool>)

private val TOOL_GROUPS = listOf(
    ToolGroup("Draw", listOf(Tool.DRAW, Tool.LINE, Tool.BOX, Tool.OVAL)),
    ToolGroup("Edit", listOf(Tool.SELECT, Tool.POINTS, Tool.ERASE)),
    ToolGroup("Add", listOf(Tool.FILL, Tool.HARDWARE)),
)

private val Tool.hint: String
    get() = when (this) {
        Tool.DRAW -> "Freehand, cleaned up"
        Tool.LINE -> "Drag a straight line"
        Tool.BOX -> "Drag corner to corner"
        Tool.OVAL -> "Drag corner to corner"
        Tool.SELECT -> "Pick, move and change pieces"
        Tool.POINTS -> "Move, add and shape points"
        Tool.ERASE -> "Tap a line to remove it"
        Tool.FILL -> "Tap inside to colour"
        Tool.HARDWARE -> "Rivets, snaps, buttons"
    }

/** A chip that opens a menu; shows ▾ so it reads as a menu. */
@Composable
private fun MenuChip(
    label: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text("$label ▾") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

@Composable
private fun ToolBar(state: SketchState, vm: DesignsViewModel) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp)) {
        // Main row: tool groups, grid settings, undo/redo.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TOOL_GROUPS.forEach { group ->
                val active = state.tool in group.tools
                MenuChip(
                    label = if (active) state.tool.label else group.label,
                    selected = active,
                    modifier = Modifier.testTag("group-${group.label}"),
                ) { close ->
                    group.tools.forEach { tool ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(tool.label)
                                    Text(tool.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingIcon = { if (state.tool == tool) Text("✓") },
                            onClick = { vm.setTool(tool); close() },
                            modifier = Modifier.testTag("tool-${tool.name}"),
                        )
                    }
                }
            }
            MenuChip("Grid") { close ->
                DropdownMenuItem(
                    text = { Text("Snap to grid") },
                    trailingIcon = { if (state.options.snapToGrid) Text("✓") },
                    onClick = { vm.toggleSnap(); close() },
                )
                DropdownMenuItem(
                    text = { Text("Auto-straighten") },
                    trailingIcon = { if (state.options.straighten) Text("✓") },
                    onClick = { vm.toggleStraighten(); close() },
                )
            }
            VerticalDivider(Modifier.height(24.dp))
            IconButton(onClick = vm::undo, enabled = state.canUndo) { Text("↶", fontSize = 22.sp) }
            IconButton(onClick = vm::redo, enabled = state.canRedo) { Text("↷", fontSize = 22.sp) }
        }

        // Second row: what the current tool can do.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.tool) {
                Tool.SELECT -> SelectActions(state, vm)
                Tool.POINTS -> PointActions(state, vm)
                Tool.FILL -> FillSwatches(state, vm)
                Tool.HARDWARE -> {
                    Stamp.entries.forEach { stamp ->
                        FilterChip(selected = state.stamp == stamp, onClick = { vm.setStamp(stamp) }, label = { Text(stamp.label) })
                    }
                    Hint("Tap to place")
                }
                else -> Hint(state.tool.hint)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SelectActions(state: SketchState, vm: DesignsViewModel) {
    if (state.selection.isEmpty()) {
        Hint("Tap pieces to select them, then drag to move")
        return
    }
    Text("${state.selection.size} selected", style = MaterialTheme.typography.labelLarge)
    val grouped = state.selection.any { state.shapes.getOrNull(it)?.group != null }
    MenuChip("Arrange", modifier = Modifier.testTag("menu-arrange")) { close ->
        DropdownMenuItem(text = { Text(if (grouped) "Ungroup" else "Group") }, onClick = {
            if (grouped) vm.ungroupSelection() else vm.groupSelection(); close()
        })
        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { vm.duplicateSelection(mirror = false); close() })
        DropdownMenuItem(text = { Text("Mirror copy") }, onClick = { vm.duplicateSelection(mirror = true); close() })
        DropdownMenuItem(text = { Text("Flip") }, onClick = { vm.flipSelection(); close() })
    }
    val stitched = state.selection.any { Edits.topstitchOf(state.shapes, it) != null }
    MenuChip("Stitching", modifier = Modifier.testTag("menu-stitching")) { close ->
        DropdownMenuItem(
            text = { Text(if (stitched) "Remove topstitch" else "Topstitch inside") },
            onClick = { vm.topstitchSelection(); close() },
            modifier = Modifier.testTag("action-topstitch"),
        )
        DropdownMenuItem(text = { Text("Make stitch line / solid line") }, onClick = { vm.toggleStitchStyle(); close() })
        HorizontalDivider()
        FillPattern.entries.forEach { pattern ->
            DropdownMenuItem(text = { Text("Quilting: ${pattern.label}") }, onClick = { vm.setPattern(pattern); close() })
        }
        DropdownMenuItem(text = { Text("No quilting") }, onClick = { vm.setPattern(null); close() })
    }
    MenuChip("Corners") { close ->
        DropdownMenuItem(text = { Text("Clip all corners") }, onClick = { vm.clipCorners(); close() })
        DropdownMenuItem(text = { Text("Round all corners") }, onClick = { vm.roundCorners(); close() })
        Text(
            "For one corner, use Edit › Points",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    TextButton(onClick = vm::deleteSelection) { Text("Delete") }
    TextButton(onClick = vm::clearSelection) { Text("Done") }
}

@Composable
private fun PointActions(state: SketchState, vm: DesignsViewModel) {
    val point = state.selectedPoint
    val shape = state.selectedShape?.let { state.shapes.getOrNull(it) }
    when {
        shape == null -> Hint("Tap a line to edit its points")
        point == null -> Hint("Drag a point to move it. Tap the line to add one.")
        else -> {
            val isCorner = shape.corners.getOrElse(point) { true }
            FilterChip(selected = isCorner, onClick = { if (!isCorner) vm.toggleSelectedCorner() }, label = { Text("Corner") })
            FilterChip(selected = !isCorner, onClick = { if (isCorner) vm.toggleSelectedCorner() }, label = { Text("Smooth") })
            if (isCorner) {
                TextButton(onClick = { vm.styleSelectedCorner(Edits.CornerStyle.CLIPPED) }) { Text("Clip") }
                TextButton(onClick = { vm.styleSelectedCorner(Edits.CornerStyle.ROUNDED) }) { Text("Round") }
            }
            TextButton(onClick = vm::deleteSelectedPoint) { Text("Delete point") }
        }
    }
}

@Composable
private fun FillSwatches(state: SketchState, vm: DesignsViewModel) {
    FILL_COLORS.forEach { color ->
        val selected = state.fillColor == color
        Box(
            Modifier
                .size(32.dp)
                .background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.surface, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                )
                .clickable { vm.setFillColor(color) },
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) Text("✕", fontSize = 14.sp)
        }
    }
    Hint("Tap inside a shape")
}
