package com.boro.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boro.notes.data.Design
import com.boro.notes.data.Idea
import com.boro.notes.data.IdeaKind
import com.boro.notes.studio.Collage
import com.boro.notes.studio.CollageItem
import com.boro.notes.studio.CollageKind
import com.boro.notes.studio.StudioJson
import java.util.UUID
import kotlin.math.roundToInt

const val TAG_SKETCH_BOARD = "sketchBoard"
const val TAG_NEW_IDEA = "newIdea"
const val TAG_IDEA_PREFIX = "idea-"
const val TAG_COLLAGE = "collageEditor"

private val NOTE_COLOURS = listOf(0xFFF3E3A6, 0xFFF2C7B8, 0xFFCFE0C3, 0xFFC9D6EA, 0xFFFFFFFF, 0xFF1E2A47)
private val BOARD_PAPERS = listOf("Cream" to 0xFFF6EFDF, "Kraft" to 0xFFD9C3A0, "Cork" to 0xFFC49A6C, "Indigo" to 0xFF1F2A44, "Charcoal" to 0xFF2A2A2A, "White" to 0xFFFFFFFF)

/** The Sketch tab: a grid of quick ideas (sketches and collages), filtered by collection. */
@Composable
fun SketchBoard(vm: IdeasViewModel, designs: List<Design>, modifier: Modifier = Modifier) {
    val shown by vm.shown.collectAsStateWithLifecycle()
    val all by vm.ideas.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val current by vm.collection.collectAsStateWithLifecycle()
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().testTag(TAG_SKETCH_BOARD)) {
        Column(Modifier.fillMaxSize()) {
            if (collections.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip(selected = current == null, onClick = { vm.showCollection(null) }, label = { Text("All ${all.size}") }) }
                    items(collections) { c ->
                        FilterChip(
                            selected = current == c,
                            onClick = { vm.showCollection(if (current == c) null else c) },
                            leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(16.dp)) },
                            label = { Text("$c ${all.count { it.collection == c }}") },
                            modifier = Modifier.testTag("collection-$c"),
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Lightbulb, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Quick ideas live here", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Sketch a silhouette, trace over a photo of something you spotted, or pin ideas together in a collage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { idea ->
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(cs.surfaceContainer)
                                .clickable { vm.openIdea(idea.id) }
                                .testTag("$TAG_IDEA_PREFIX${idea.id}"),
                        ) {
                            IdeaThumbnail(idea, all, designs, Modifier.fillMaxWidth().aspectRatio(if (idea.kind == IdeaKind.SKETCH) 0.75f else 1f))
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (idea.kind == IdeaKind.SKETCH) Icons.Filled.Brush else Icons.Filled.Dashboard, null,
                                        tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        idea.title.ifBlank { if (idea.kind == IdeaKind.SKETCH) "Sketch" else "Collage" },
                                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (idea.note.isNotBlank()) {
                                    Text(idea.note, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        NewIdeaFab(vm, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp))
    }
}

@Composable
private fun NewIdeaFab(vm: IdeasViewModel, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val photos = rememberPhotoSource { file -> vm.create(IdeaKind.SKETCH, photo = file) }
    Box(modifier) {
        FloatingActionButton(onClick = { open = true }, modifier = Modifier.testTag(TAG_NEW_IDEA)) {
            Icon(Icons.Filled.Add, contentDescription = "New idea")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Sketch") }, leadingIcon = { Icon(Icons.Filled.Brush, null) },
                onClick = { open = false; vm.create(IdeaKind.SKETCH) }, modifier = Modifier.testTag("new-sketch"),
            )
            DropdownMenuItem(
                text = { Text("Trace a photo") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                onClick = { open = false; photos.camera() },
            )
            DropdownMenuItem(
                text = { Text("Trace from gallery") }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) },
                onClick = { open = false; photos.gallery() },
            )
            DropdownMenuItem(
                text = { Text("Collage") }, leadingIcon = { Icon(Icons.Filled.Dashboard, null) },
                onClick = { open = false; vm.create(IdeaKind.COLLAGE) }, modifier = Modifier.testTag("new-collage"),
            )
        }
    }
}

@Composable
fun IdeaThumbnail(idea: Idea, ideas: List<Idea>, designs: List<Design>, modifier: Modifier, depth: Int = 0) {
    when (idea.kind) {
        IdeaKind.SKETCH -> PadThumbnail(remember(idea.content) { StudioJson.pad(idea.content) }, modifier)
        IdeaKind.COLLAGE -> CollageView(remember(idea.content) { StudioJson.collage(idea.content) }, ideas, designs, modifier, depth = depth)
    }
}

/** Title, one-line note, collection and delete, above a sketch or collage. */
@Composable
internal fun IdeaHeader(draft: IdeaDraft, vm: IdeasViewModel, collections: List<String>, what: String) {
    val cs = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::close, modifier = Modifier.testTag(TAG_BACK)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            PlainField(draft.idea.title, vm::rename, "Name this $what", MaterialTheme.typography.titleMedium, Modifier.testTag("idea-title"))
            PlainField(draft.idea.note, vm::setNote, "Add a note", MaterialTheme.typography.bodySmall.copy(color = cs.onSurfaceVariant))
        }
        Box {
            var open by remember { mutableStateOf(false) }
            FilterChip(
                selected = draft.idea.collection != null,
                onClick = { open = true },
                leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(16.dp)) },
                label = { Text(draft.idea.collection ?: "Collection", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = IntrinsicWidthCap)) },
                modifier = Modifier.testTag("idea-collection"),
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                MenuLabel("Group it in")
                collections.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c) },
                        trailingIcon = { if (c == draft.idea.collection) Text("✓") },
                        onClick = { vm.setCollection(c); open = false },
                    )
                }
                DropdownMenuItem(
                    text = { Text("New collection…") }, leadingIcon = { Icon(Icons.Filled.Add, null) },
                    onClick = { open = false; naming = true }, modifier = Modifier.testTag("new-collection"),
                )
                if (draft.idea.collection != null) {
                    DropdownMenuItem(text = { Text("Not in a collection") }, onClick = { vm.setCollection(null); open = false })
                }
            }
        }
        Box {
            var open by remember { mutableStateOf(false) }
            IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Delete $what") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { open = false; confirmDelete = true },
                )
            }
        }
    }
    if (confirmDelete) ConfirmDeleteDialog("this $what", onConfirm = vm::deleteOpen, onDismiss = { confirmDelete = false })
    if (naming) {
        TextPrompt("New collection", "Pockets, hems, silhouettes…", "", onDone = { vm.setCollection(it) }, onDismiss = { naming = false })
    }
}

private val IntrinsicWidthCap: Dp = 96.dp

/** A borderless one-line text field with a faint placeholder. */
@Composable
internal fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val textStyle = if (style.color == Color.Unspecified) style.copy(color = cs.onSurface) else style
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = textStyle,
        cursorBrush = SolidColor(cs.primary),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = cs.onSurfaceVariant.copy(alpha = 0.6f)))
                inner()
            }
        },
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

/** A small dialog asking for one line of text. */
@Composable
internal fun TextPrompt(title: String, hint: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit, singleLine: Boolean = true) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, placeholder = { Text(hint) }, singleLine = singleLine,
                modifier = Modifier.fillMaxWidth().testTag("prompt-field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); if (text.isNotBlank()) onDone(text.trim()) }, modifier = Modifier.testTag("prompt-ok")) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The open idea: sketch pad or collage. */
@Composable
fun IdeaEditor(draft: IdeaDraft, vm: IdeasViewModel, designs: List<Design>) {
    val collections by vm.collections.collectAsStateWithLifecycle()
    when (draft.idea.kind) {
        IdeaKind.SKETCH -> SketchPadScreen(draft, vm, collections)
        IdeaKind.COLLAGE -> CollageEditor(draft, vm, collections, designs)
    }
}

/**
 * Draws a collage. With [onSelect]/[onMove] it's live: tap picks an item, one finger drags it,
 * two fingers resize and turn it; otherwise it's a read-only thumbnail.
 */
@Composable
fun CollageView(
    collage: Collage,
    ideas: List<Idea>,
    designs: List<Design>,
    modifier: Modifier,
    depth: Int = 0,
    selected: String? = null,
    onSelect: ((String?) -> Unit)? = null,
    onGestureStart: () -> Unit = {},
    onMove: ((CollageItem) -> Unit)? = null,
) {
    val paper = Color(collage.paper ?: 0xFFF6EFDF)
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .background(paper)
            .then(if (onSelect != null) Modifier.pointerInput(Unit) { detectTapGestures { onSelect(null) } } else Modifier),
    ) {
        val boardPx = with(LocalDensity.current) { maxWidth.toPx() }
        val board = maxWidth
        collage.items.forEach { item -> key(item.id) {
            val latest by rememberUpdatedState(item)
            val isSelected = item.id == selected
            Box(
                Modifier
                    .offset { IntOffset((item.x * boardPx).roundToInt(), (item.y * boardPx).roundToInt()) }
                    .width(board * item.w)
                    .then(
                        if (onSelect != null && onMove != null) Modifier.pointerInput(item.id, boardPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                onSelect(latest.id)
                                down.consume()
                                var started = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.none { it.pressed }) break
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()
                                    val turn = event.calculateRotation()
                                    if (pan == androidx.compose.ui.geometry.Offset.Zero && zoom == 1f && turn == 0f) continue
                                    if (!started) { started = true; onGestureStart() }
                                    val cur = latest
                                    onMove(
                                        cur.copy(
                                            x = (cur.x + pan.x / boardPx).coerceIn(-cur.w * 0.8f, 0.95f),
                                            y = (cur.y + pan.y / boardPx).coerceIn(-0.2f, 0.95f),
                                            w = (cur.w * zoom).coerceIn(0.08f, 1.2f),
                                            rot = cur.rot + turn,
                                        )
                                    )
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } else Modifier
                    )
                    .rotate(item.rot)
                    .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
                    .testTag("collage-item-${item.id}"),
            ) {
                CollageItemContent(item, ideas, designs, board, depth)
            }
        } }
    }
}

@Composable
private fun CollageItemContent(item: CollageItem, ideas: List<Idea>, designs: List<Design>, board: Dp, depth: Int) {
    when (item.kind) {
        CollageKind.PHOTO -> {
            val bitmap = rememberStoredImage(item.ref)
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap, null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)),
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black.copy(alpha = 0.08f)))
            }
        }
        CollageKind.IDEA -> {
            val idea = ideas.firstOrNull { it.id.toString() == item.ref }
            when {
                idea == null -> Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(Color.Black.copy(alpha = 0.08f)))
                idea.kind == IdeaKind.COLLAGE && depth >= 1 -> Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.Black.copy(alpha = 0.08f)))
                else -> IdeaThumbnail(
                    idea, ideas, designs,
                    Modifier.fillMaxWidth().aspectRatio(if (idea.kind == IdeaKind.SKETCH) 0.75f else 1f).border(0.5.dp, Color.Black.copy(alpha = 0.15f)),
                    depth = depth + 1,
                )
            }
        }
        CollageKind.DESIGN -> {
            val design = designs.firstOrNull { it.id.toString() == item.ref }
            Box(Modifier.fillMaxWidth().aspectRatio(0.75f).background(Color(0xFFFBF8F1)).border(0.5.dp, Color.Black.copy(alpha = 0.15f))) {
                if (design != null) DesignThumbnail(design, Modifier.fillMaxSize(), ink = Color(0xFF2B2B2B), background = Color(0xFFFBF8F1))
            }
        }
        CollageKind.TEXT -> {
            val bg = Color(item.color)
            val ink = if (item.color == 0xFF1E2A47) Color(0xFFF4F1EA) else Color(0xFF2B2B2B)
            Box(Modifier.fillMaxWidth().background(bg).padding(board * 0.02f)) {
                Text(item.text.ifBlank { "Note" }, color = ink, fontSize = (board.value * 0.04f).coerceAtLeast(4f).sp, lineHeight = (board.value * 0.05f).coerceAtLeast(5f).sp)
            }
        }
    }
}

/** A collage editor: a square board where photos, sketches, designs and notes are pinned up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollageEditor(draft: IdeaDraft, vm: IdeasViewModel, collections: List<String>, designs: List<Design>) {
    val cs = MaterialTheme.colorScheme
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<CollageKind?>(null) }
    var editingText by remember { mutableStateOf<CollageItem?>(null) }
    val collage = draft.collage
    val selectedItem = collage.items.firstOrNull { it.id == selected }

    fun add(kind: CollageKind, ref: String = "", text: String = "", w: Float = 0.42f) {
        val n = collage.items.size
        val item = CollageItem(
            id = UUID.randomUUID().toString(), kind = kind, ref = ref, text = text,
            x = 0.08f + (n % 5) * 0.07f, y = 0.08f + (n % 4) * 0.08f, w = w,
            rot = listOf(-4f, 3f, -2f, 5f, 0f)[n % 5],
        )
        vm.changeCollage { it.copy(items = it.items + item) }
        selected = item.id
    }

    val photos = rememberPhotoSource { file -> add(CollageKind.PHOTO, ref = file) }

    Surface(Modifier.fillMaxSize().testTag(TAG_COLLAGE), color = cs.surface) {
        Column(Modifier.fillMaxSize()) {
            IdeaHeader(draft, vm, collections, what = "collage")
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().background(cs.surfaceContainerHighest.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight) - 12.dp
                CollageView(
                    collage, ideas, designs,
                    Modifier.size(side).clip(RoundedCornerShape(6.dp)),
                    selected = selected,
                    onSelect = { selected = it },
                    onGestureStart = vm::checkpoint,
                    onMove = { vm.updateItem(it) },
                )
            }
            FlowRow(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Add things.
                ToolGroup(cs.primaryContainer, cs.onPrimaryContainer) {
                    ToolMenu(Icons.Filled.AddPhotoAlternate, "Add a photo", "collage-photo") { close ->
                        DropdownMenuItem(text = { Text("Take a photo") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) }, onClick = { close(); photos.camera() })
                        DropdownMenuItem(text = { Text("From gallery") }, leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null) }, onClick = { close(); photos.gallery() })
                    }
                    ToolButton(Icons.Filled.Collections, "Add a saved idea", "collage-idea") { picking = CollageKind.IDEA }
                    ToolButton(Icons.Filled.Checkroom, "Add a design", "collage-design") { picking = CollageKind.DESIGN }
                    ToolButton(Icons.Filled.TextFields, "Add a note", "collage-text") { editingText = CollageItem(id = "", kind = CollageKind.TEXT) }
                }
                // The picked item.
                if (selectedItem != null) {
                    ToolGroup(cs.secondaryContainer, cs.onSecondaryContainer) {
                        if (selectedItem.kind == CollageKind.TEXT) {
                            ToolButton(Icons.Filled.Edit, "Edit note", "collage-edit") { editingText = selectedItem }
                            ToolMenu(Icons.Filled.Palette, "Note colour", "collage-note-colour", accent = Color(selectedItem.color)) { close ->
                                SwatchGrid(NOTE_COLOURS.mapIndexed { i, c -> Swatch("Note ${i + 1}", Color(c), "note-colour-$i", c == selectedItem.color) }) { s ->
                                    vm.updateItem(selectedItem.copy(color = NOTE_COLOURS[s.tag.removePrefix("note-colour-").toInt()]), record = true)
                                    close()
                                }
                            }
                        }
                        ToolButton(Icons.Filled.FlipToFront, "Bring to front", "collage-front") {
                            vm.changeCollage { c -> c.copy(items = c.items.filter { it.id != selectedItem.id } + selectedItem) }
                        }
                        ToolButton(Icons.Filled.FlipToBack, "Send to back", "collage-back") {
                            vm.changeCollage { c -> c.copy(items = listOf(selectedItem) + c.items.filter { it.id != selectedItem.id }) }
                        }
                        ToolButton(Icons.Filled.Delete, "Remove from collage", "collage-remove") {
                            vm.changeCollage { c -> c.copy(items = c.items.filter { it.id != selectedItem.id }) }
                            selected = null
                        }
                    }
                }
                // The board.
                ToolGroup(cs.tertiaryContainer, cs.onTertiaryContainer) {
                    ToolMenu(Icons.Filled.Palette, "Board colour", "collage-paper", accent = Color(collage.paper ?: 0xFFF6EFDF)) { close ->
                        MenuLabel("Board")
                        SwatchGrid(BOARD_PAPERS.mapIndexed { i, (name, c) -> Swatch(name, Color(c), "board-$i", (collage.paper ?: 0xFFF6EFDF) == c) }) { s ->
                            val c = BOARD_PAPERS[s.tag.removePrefix("board-").toInt()].second
                            vm.changeCollage { it.copy(paper = c) }
                            close()
                        }
                    }
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", "collage-undo", enabled = draft.canUndo) { vm.undo() }
                    ToolButton(Icons.AutoMirrored.Filled.Redo, "Redo", "collage-redo", enabled = draft.canRedo) { vm.redo() }
                }
            }
            if (collage.isEmpty) {
                Text(
                    "Add photos, saved ideas, designs and notes. Drag with one finger; pinch to resize and turn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).navigationBarsPadding(),
                )
            }
        }
    }

    picking?.let { kind ->
        PickerDialog(
            title = if (kind == CollageKind.IDEA) "Add a saved idea" else "Add a design",
            entries = if (kind == CollageKind.IDEA) {
                ideas.filter { it.id != draft.idea.id }.map { it.id to it.title.ifBlank { if (it.kind == IdeaKind.SKETCH) "Sketch" else "Collage" } }
            } else designs.map { it.id to it.name.ifBlank { "Untitled design" } },
            empty = if (kind == CollageKind.IDEA) "No other ideas saved yet." else "No designs yet.",
            onPick = { id -> add(kind, ref = id.toString(), w = 0.34f) },
            onDismiss = { picking = null },
        )
    }
    editingText?.let { item ->
        TextPrompt(
            if (item.id.isEmpty()) "New note" else "Edit note", "Fabric, a detail, a measurement…", item.text,
            singleLine = false,
            onDone = { text ->
                if (item.id.isEmpty()) add(CollageKind.TEXT, text = text, w = 0.3f)
                else vm.updateItem(item.copy(text = text), record = true)
            },
            onDismiss = { editingText = null },
        )
    }
}

/** A list of things to pick one from. */
@Composable
internal fun PickerDialog(title: String, entries: List<Pair<Long, String>>, empty: String, onPick: (Long) -> Unit, onDismiss: () -> Unit, picked: Set<Long> = emptySet()) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (entries.isEmpty()) Text(empty)
            else LazyColumn(Modifier.height(320.dp)) {
                items(entries) { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(id); onDismiss() }.padding(12.dp).testTag("pick-$id"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (id in picked) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
