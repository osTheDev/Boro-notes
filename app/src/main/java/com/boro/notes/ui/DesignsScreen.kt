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
import com.boro.notes.sketch.SketchCodec

const val TAG_DESIGN_LIST = "designList"
const val TAG_DESIGN_BOARD = "designBoard"
const val TAG_NEW_DESIGN = "newDesign"
const val TAG_SKETCH_EDITOR = "sketchEditor"
const val TAG_DESIGN_PREFIX = "design-"
const val TAG_LINKED_NOTE = "linkedNote"
const val TAG_LINK_NOTE_BUTTON = "linkNote"

/** Tools that drag out a perfect shape and preview it while dragging. */
private val SHAPE_TOOLS = setOf(Tool.LINE, Tool.BOX, Tool.OVAL)

@Composable
fun DesignsScreen(designsVm: DesignsViewModel, notesVm: NotesViewModel, wide: Boolean) {
    val sketch by designsVm.sketch.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { designsVm.flush() }

    val current = sketch
    if (current == null) {
        if (wide) DesignBoard(designsVm) else DesignList(designsVm)
    } else {
        BackHandler { designsVm.close() }
        SketchEditor(current, designsVm, notesVm, wide)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesignList(vm: DesignsViewModel) {
    val designs by vm.filtered.collectAsStateWithLifecycle()
    val all by vm.all.collectAsStateWithLifecycle()
    val filter by vm.stageFilter.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag(TAG_DESIGN_LIST),
        topBar = { TopAppBar(title = { Text("Designs") }) },
        floatingActionButton = { NewDesignFab(vm) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = filter == null, onClick = { vm.setStageFilter(null) }, label = { Text("All ${all.size}") })
                }
                items(Stage.entries) { stage ->
                    val count = all.count { it.stage == stage }
                    FilterChip(
                        selected = filter == stage,
                        onClick = { vm.setStageFilter(if (filter == stage) null else stage) },
                        label = { Text("${stage.label} $count") },
                    )
                }
            }
            if (designs.isEmpty()) {
                EmptyDesigns(Modifier.fillMaxSize())
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(designs, key = { it.id }) { design ->
                        ListItem(
                            leadingContent = { Thumbnail(design, Modifier.size(56.dp)) },
                            headlineContent = { Text(design.name.ifBlank { "Untitled design" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(design.stage.label) },
                            trailingContent = { NextStageButton(design, vm) },
                            modifier = Modifier.clickable { vm.open(design.id) }.testTag("$TAG_DESIGN_PREFIX${design.id}"),
                        )
                    }
                }
            }
        }
    }
}

/** Inner screen: the development tracker as a board with one column per stage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesignBoard(vm: DesignsViewModel) {
    val all by vm.all.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.testTag(TAG_DESIGN_BOARD),
        topBar = { TopAppBar(title = { Text("Designs") }) },
        floatingActionButton = { NewDesignFab(vm) },
    ) { padding ->
        if (all.isEmpty()) {
            EmptyDesigns(Modifier.padding(padding).fillMaxSize())
        } else LazyRow(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Stage.entries) { stage ->
                val inStage = all.filter { it.stage == stage }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(200.dp).fillMaxHeight(),
                ) {
                    Column {
                        Text(
                            "${stage.label}  ${inStage.size}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(12.dp),
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(inStage, key = { it.id }) { design ->
                                Card(Modifier.fillMaxWidth().clickable { vm.open(design.id) }.testTag("$TAG_DESIGN_PREFIX${design.id}")) {
                                    Thumbnail(design, Modifier.fillMaxWidth().height(110.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            design.name.ifBlank { "Untitled design" },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                                        )
                                        NextStageButton(design, vm)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewDesignFab(vm: DesignsViewModel) {
    FloatingActionButton(onClick = vm::createDesign, modifier = Modifier.testTag(TAG_NEW_DESIGN)) {
        Icon(Icons.Filled.Add, contentDescription = "New design")
    }
}

@Composable
private fun NextStageButton(design: Design, vm: DesignsViewModel) {
    if (design.stage == Stage.DONE) return
    IconButton(onClick = { vm.advanceStage(design.id) }) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move to ${design.stage.next().label}")
    }
}

@Composable
private fun EmptyDesigns(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("No designs yet. Tap + to sketch one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Thumbnail(design: Design, modifier: Modifier) {
    val shapes = remember(design.shapes) { SketchCodec.decode(design.shapes) }
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) { drawThumbnail(shapes, ink) }
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
                    StagePicker(state.stage, vm::setStage)
                    if (!wide) {
                        TextButton(onClick = {
                            if (state.noteId == null) vm.createLinkedNote()
                            showNote = true
                        }) { Text("Note") }
                    }
                    EditorOverflow(vm)
                },
            )
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.weight(if (wide) 0.62f else 1f).fillMaxHeight()) {
                SketchCanvas(
                    shapes = state.shapes,
                    onStroke = vm::addStroke,
                    onTap = vm::tap,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    previewShape = if (state.tool in SHAPE_TOOLS) vm::shapeFor else null,
                )
                HorizontalDivider()
                ToolBar(state, vm)
            }
            if (wide) {
                VerticalDivider()
                LinkedNotePane(state, noteEditor, vm, notesVm, onBack = null, Modifier.weight(0.38f).fillMaxHeight())
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
            EditorPane(note, notesVm, onBack = onBack, showDelete = false)
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
private fun StagePicker(stage: Stage, onPick: (Stage) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(stage.label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Stage.entries.forEach {
                DropdownMenuItem(text = { Text(it.label) }, onClick = { onPick(it); open = false })
            }
        }
    }
}

@Composable
private fun EditorOverflow(vm: DesignsViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Clear canvas") }, onClick = { vm.clearCanvas(); open = false })
            DropdownMenuItem(text = { Text("Unlink note") }, onClick = { vm.unlinkNote(); open = false })
            DropdownMenuItem(
                text = { Text("Delete design") },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = { vm.deleteOpenDesign(); open = false },
            )
        }
    }
}

@Composable
private fun ToolBar(state: SketchState, vm: DesignsViewModel) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tool.entries.forEach { tool ->
                FilterChip(
                    selected = state.tool == tool,
                    onClick = { vm.setTool(tool) },
                    label = { Text(tool.label) },
                    modifier = Modifier.testTag("tool-${tool.name}"),
                )
            }
            VerticalDivider(Modifier.height(24.dp))
            FilterChip(selected = state.options.snapToGrid, onClick = vm::toggleSnap, label = { Text("Snap") })
            FilterChip(selected = state.options.straighten, onClick = vm::toggleStraighten, label = { Text("Straighten") })
            VerticalDivider(Modifier.height(24.dp))
            IconButton(onClick = vm::undo, enabled = state.canUndo) { Text("↶", fontSize = 22.sp) }
            IconButton(onClick = vm::redo, enabled = state.canRedo) { Text("↷", fontSize = 22.sp) }
        }
        if (state.tool == Tool.FILL) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
            }
        }
    }
}
