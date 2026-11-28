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
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.GridView
import com.boro.notes.studio.Board
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
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
/** A small pill button over the board. */
@Composable
private fun BoardChip(text: String, bg: Color, fg: Color, tag: String, onClick: () -> Unit) {
    Text(
        text, color = fg, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp).testTag(tag),
    )
}

/** One collage item full screen: a photo you can pinch to zoom, a big sticky note, a sketch or a design. */
@Composable
private fun CollageItemViewer(item: CollageItem, ideas: List<Idea>, designs: List<Design>, onChange: (CollageItem) -> Unit, onClose: () -> Unit) {
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var pinning by remember { mutableStateOf(false) }
    var openPin by remember { mutableStateOf<Int?>(null) }
    var newPinAt by remember { mutableStateOf<Offset?>(null) }
    var editPin by remember { mutableStateOf<Int?>(null) }
    val current by rememberUpdatedState(item)
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize().testTag("collage-viewer"), color = Color(0xFF151515), contentColor = Color.White) {
            Box(Modifier.fillMaxSize().systemBarsPadding()) {
                BoxWithConstraints(
                    Modifier.fillMaxSize().padding(16.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, move, scale, _ ->
                                zoom = (zoom * scale).coerceIn(1f, 6f)
                                pan = if (zoom == 1f) Offset.Zero else pan + move
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }) },
                    contentAlignment = Alignment.Center,
                ) {
                    val big = minOf(maxWidth, maxHeight)
                    val maxW = maxWidth
                    val maxH = maxHeight
                    Box(Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }, contentAlignment = Alignment.Center) {
                        when (item.kind) {
                            CollageKind.PHOTO -> {
                                val bitmap = rememberStoredImage(item.ref)
                                val aspect = bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 1f
                                val w = if (maxW / maxH > aspect) maxH * aspect else maxW
                                val h = w / aspect
                                Box(
                                    Modifier.size(w, h).pointerInput(pinning) {
                                        detectTapGestures { at ->
                                            if (pinning) newPinAt = Offset(at.x / size.width, at.y / size.height) else openPin = null
                                        }
                                    },
                                ) {
                                    if (bitmap != null) androidx.compose.foundation.Image(bitmap, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                    item.pins.forEachIndexed { i, pin ->
                                        Box(
                                            Modifier
                                                .offset(w * pin.x - 14.dp, h * pin.y - 14.dp)
                                                .size(28.dp)
                                                .background(if (openPin == i) MaterialTheme.colorScheme.primary else YARN, CircleShape)
                                                .border(2.dp, Color.White, CircleShape)
                                                .clickable { openPin = if (openPin == i) null else i }
                                                .testTag("collage-pin-$i"),
                                            contentAlignment = Alignment.Center,
                                        ) { Text("${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                                    }
                                }
                            }
                            CollageKind.TEXT -> {
                                val ink = if (item.color == 0xFF1E2A47) Color(0xFFF4F1EA) else Color(0xFF2B2B2B)
                                Box(Modifier.width(big).heightIn(min = big * 0.8f).background(Color(item.color)).padding(24.dp)) {
                                    Text(item.text.ifBlank { "Note" }, color = ink, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                            CollageKind.IDEA, CollageKind.DESIGN -> Box(Modifier.width(big)) {
                                CollageItemContent(item.copy(w = 1f), ideas, designs, big, depth = 0)
                            }
                        }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).testTag("collage-viewer-close")) {
                    Icon(Icons.Filled.Close, "Close", tint = Color.White)
                }
                if (item.kind == CollageKind.PHOTO) {
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        openPin?.let { i -> item.pins.getOrNull(i) }?.let { pin ->
                            val i = openPin!!
                            Row(
                                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(start = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}. ${pin.text}", modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                                TextButton(onClick = { editPin = i }) { Text("Edit", color = Color.White) }
                                IconButton(onClick = { onChange(current.copy(pins = current.pins.filterIndexed { j, _ -> j != i })); openPin = null }) {
                                    Icon(Icons.Filled.Delete, "Remove pin", tint = Color.White)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(if (pinning) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
                                .clickable { pinning = !pinning; openPin = null }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("collage-pin-mode"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PushPin, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (pinning) "Tap a spot to pin a note · Done" else "Pin a note", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
    newPinAt?.let { at ->
        TextPrompt("Pin a note", "Rivet here, fade this seam…", "", singleLine = false, onDone = { text ->
            if (text.isNotBlank()) onChange(current.copy(pins = current.pins + com.boro.notes.studio.CollagePin(at.x, at.y, text.trim())))
        }, onDismiss = { newPinAt = null })
    }
    editPin?.let { i ->
        TextPrompt("Edit note", "", current.pins.getOrNull(i)?.text.orEmpty(), singleLine = false, onDone = { text ->
            onChange(current.copy(pins = current.pins.mapIndexed { j, p -> if (j == i) p.copy(text = text.trim()) else p }))
        }, onDismiss = { editPin = null })
    }
}

/** The red of the yarn tied between board items. */
private val YARN = Color(0xFFB3261E)

/**
 * A collage board. In the editor ([onSelect] and [onMove] given) it's endless: one finger on the
 * paper pans, two fingers zoom, and items drag, pinch and turn (snapping to right angles). As a
 * thumbnail it fits everything in.
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
    onMoveMosaic: ((Float, Float, Float) -> Unit)? = null,
    zoom: Float = 1f,
    pan: Offset = Offset.Zero,
    onView: ((Float, Offset) -> Unit)? = null,
    /** View mode: nothing moves; tapping an item opens it large. */
    onOpen: ((CollageItem) -> Unit)? = null,
) {
    val paper = Color(collage.paper ?: 0xFFF6EFDF)
    val editing = onSelect != null && onMove != null
    val viewOnly = onOpen != null
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .background(paper)
            .then(
                if (onSelect != null) Modifier.pointerInput(Unit) {
                    // The paper: tap clears the pick, one finger pans, two fingers zoom.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                            if (event.changes.any { it.isConsumed }) continue
                            val move = event.calculatePan()
                            val scale = event.calculateZoom()
                            if (!moved && (event.changes.first().position - down.position).getDistance() < viewConfiguration.touchSlop && scale == 1f) continue
                            moved = true
                            val z = currentZoom
                            val newZoom = (z * scale).coerceIn(0.15f, 4f)
                            val centroid = event.calculateCentroid()
                            val k = newZoom / z
                            onView?.invoke(newZoom, centroid - (centroid - currentPan) * k + move)
                            event.changes.forEach { it.consume() }
                        }
                        if (!moved) onSelect(null)
                    }
                } else Modifier,
            ),
    ) {
        val boardPx = with(LocalDensity.current) { maxWidth.toPx() }
        val board = maxWidth
        val slots = remember(collage) { Board.mosaicSlots(collage) }
        // A thumbnail fits everything; the editor uses its own zoom and pan.
        val (viewZoom, viewPan) = if (editing || viewOnly) zoom to pan else {
            val b = remember(collage) { Board.bounds(collage) }
            val aspect = with(LocalDensity.current) { maxHeight.toPx() } / boardPx
            val z = minOf(1f / b.w, aspect / b.h)
            z to Offset(-b.x * z * boardPx, -b.y * z * boardPx)
        }
        Box(
            Modifier.matchParentSize().graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = viewZoom
                scaleY = viewZoom
                translationX = viewPan.x
                translationY = viewPan.y
            },
        ) {
            collage.items.forEach { item -> key(item.id) {
                val latest by rememberUpdatedState(item)
                val slot = slots[item.id]
                val isSelected = item.id == selected
                val x = slot?.x ?: item.x
                val y = slot?.y ?: item.y
                val w = slot?.w ?: item.w
                Box(
                    Modifier
                        .offset { IntOffset((x * boardPx).roundToInt(), (y * boardPx).roundToInt()) }
                        .width(board * w)
                        .then(
                            if (viewOnly) Modifier.pointerInput(item.id) { detectTapGestures { onOpen!!(latest) } }
                            else if (editing) Modifier.pointerInput(item.id, boardPx, slot != null) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    onSelect!!(latest.id)
                                    down.consume()
                                    var started = false
                                    var rawRot = latest.rot
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                        val drag = event.calculatePan()
                                        val scale = event.calculateZoom()
                                        val turn = event.calculateRotation()
                                        if (drag == Offset.Zero && scale == 1f && turn == 0f) continue
                                        if (!started) { started = true; onGestureStart() }
                                        val cur = latest
                                        if (cur.inMosaic && cur.kind == CollageKind.PHOTO && onMoveMosaic != null && slots.isNotEmpty()) {
                                            // A photo in the mosaic moves (and pinch-resizes) the whole grid.
                                            onMoveMosaic(drag.x / boardPx, drag.y / boardPx, scale)
                                        } else {
                                            rawRot += turn
                                            onMove!!(
                                                cur.copy(
                                                    x = cur.x + drag.x / boardPx,
                                                    y = cur.y + drag.y / boardPx,
                                                    w = (cur.w * scale).coerceIn(0.05f, 3f),
                                                    rot = Board.snapTurn(rawRot),
                                                )
                                            )
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            } else Modifier
                        )
                        .rotate(if (slot != null) 0f else item.rot)
                        .then(if (isSelected) Modifier.border(2.dp / viewZoom.coerceAtLeast(0.2f), MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier)
                        .testTag("collage-item-${item.id}"),
                ) {
                    CollageItemContent(item, ideas, designs, board, depth, tile = slot?.let { it.h / it.w })
                }
            } }
            // Red yarn over everything, pinned at the top of each item, sagging a little.
            if (collage.links.isNotEmpty()) {
                Canvas(Modifier.matchParentSize()) {
                    val byId = collage.items.associateBy { it.id }
                    fun pin(id: String): Offset? {
                        val it = byId[id] ?: return null
                        val s = slots[id]
                        val x = (s?.x ?: it.x) + (s?.w ?: it.w) / 2
                        val y = (s?.y ?: it.y) + 0.012f
                        return Offset(x * boardPx, y * boardPx)
                    }
                    val stroke = 2.2.dp.toPx() / viewZoom.coerceAtLeast(0.3f)
                    collage.links.forEach { link ->
                        val a = pin(link.from) ?: return@forEach
                        val b = pin(link.to) ?: return@forEach
                        val mid = (a + b) / 2f
                        val sag = (b - a).getDistance() * 0.12f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(a.x, a.y)
                            quadraticTo(mid.x, mid.y + sag, b.x, b.y)
                        }
                        drawPath(path, YARN, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    collage.links.flatMap { listOf(it.from, it.to) }.distinct().forEach { id ->
                        val p = pin(id) ?: return@forEach
                        drawCircle(Color(0xFF7A1A14), stroke * 2.2f, p)
                        drawCircle(Color.White.copy(alpha = 0.5f), stroke * 0.8f, p - Offset(stroke * 0.6f, stroke * 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageItemContent(item: CollageItem, ideas: List<Idea>, designs: List<Design>, board: Dp, depth: Int, tile: Float? = null) {
    when (item.kind) {
        CollageKind.PHOTO -> {
            val bitmap = rememberStoredImage(item.ref)
            if (tile != null) {
                // A mosaic tile: cropped to the grid's shape.
                if (bitmap != null) {
                    androidx.compose.foundation.Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f / tile))
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f / tile).background(Color.Black.copy(alpha = 0.08f)))
                }
            } else if (bitmap != null && item.pins.isNotEmpty()) {
                Box {
                    androidx.compose.foundation.Image(
                        bitmap, null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)),
                    )
                    // Pinned spots show as small red dots on the board.
                    Canvas(Modifier.matchParentSize()) {
                        item.pins.forEach { p ->
                            val c = Offset(p.x * size.width, p.y * size.height)
                            drawCircle(YARN, size.width * 0.025f, c)
                            drawCircle(Color.White, size.width * 0.025f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(size.width * 0.006f))
                        }
                    }
                }
            } else if (bitmap != null) {
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
                    Modifier.fillMaxWidth().aspectRatio(
                        if (idea.kind == IdeaKind.SKETCH) remember(idea.content) { StudioJson.pad(idea.content).let { it.padW / it.padH } } else 1f,
                    ).border(0.5.dp, Color.Black.copy(alpha = 0.15f)),
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
            // Sticky notes are square-ish and their text grows with them.
            val k = (item.w / 0.3f).coerceIn(0.6f, 4f)
            Box(Modifier.fillMaxWidth().heightIn(min = board * item.w * 0.8f).background(bg).padding(board * 0.02f * k)) {
                Text(item.text.ifBlank { "Note" }, color = ink, fontSize = (board.value * 0.04f * k).coerceAtLeast(4f).sp, lineHeight = (board.value * 0.05f * k).coerceAtLeast(5f).sp)
            }
        }
    }
}

/**
 * A collage editor: an endless board where photos, sketches, designs and sticky notes are pinned up,
 * tied together with red yarn, and photos can snap into a mosaic.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollageEditor(draft: IdeaDraft, vm: IdeasViewModel, collections: List<String>, designs: List<Design>) {
    val cs = MaterialTheme.colorScheme
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<CollageKind?>(null) }
    var pickIdeas by remember { mutableStateOf(IdeaKind.SKETCH) }
    var editingText by remember { mutableStateOf<CollageItem?>(null) }
    /** The item yarn is being tied from; the next item tapped gets the other end. */
    var tyingFrom by remember { mutableStateOf<String?>(null) }
    /** View mode: nothing moves, and a tap opens a photo, note, sketch or design large. */
    var viewMode by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<CollageItem?>(null) }
    var zoom by remember(draft.idea.id) { mutableStateOf(1f) }
    var pan by remember(draft.idea.id) { mutableStateOf(Offset.Zero) }
    val collage = draft.collage
    val selectedItem = collage.items.firstOrNull { it.id == selected }
    /** The board's size on screen in px (width, height), so new things land in view. */
    val viewPx = remember { floatArrayOf(1f, 1f) }

    fun add(kind: CollageKind, ref: String = "", text: String = "", w: Float = 0.42f) {
        val n = collage.items.size
        // Drop new things near the middle of what's on screen, fanned out a little.
        val vw = viewPx[0].coerceAtLeast(1f)
        val cx = (vw * 0.5f - pan.x) / zoom / vw
        val cy = (viewPx[1] * 0.45f - pan.y) / zoom / vw
        val item = CollageItem(
            id = UUID.randomUUID().toString(), kind = kind, ref = ref, text = text,
            x = cx - w / 2 + (n % 5 - 2) * 0.04f, y = cy - w / 2 + (n % 4 - 1.5f) * 0.04f, w = w,
            rot = listOf(-4f, 3f, -2f, 5f, 0f)[n % 5],
            inMosaic = kind == CollageKind.PHOTO && collage.mosaic.on,
        )
        vm.changeCollage { it.copy(items = it.items + item) }
        selected = item.id
    }

    val photos = rememberPhotoSource { file -> add(CollageKind.PHOTO, ref = file) }

    Surface(Modifier.fillMaxSize().testTag(TAG_COLLAGE), color = cs.surface) {
        Column(Modifier.fillMaxSize()) {
            IdeaHeader(draft, vm, collections, what = "collage")
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val dens = LocalDensity.current
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    viewPx[0] = with(dens) { maxWidth.toPx() }
                    viewPx[1] = with(dens) { maxHeight.toPx() }
                    CollageView(
                        collage, ideas, designs,
                        Modifier.fillMaxSize(),
                        selected = selected,
                        onSelect = { id ->
                            val from = tyingFrom
                            if (from != null && id != null && id != from) {
                                vm.changeCollage { Board.toggleLink(it, from, id) }
                                tyingFrom = null
                                selected = id
                            } else {
                                if (id == null) tyingFrom = null
                                selected = id
                            }
                        },
                        onGestureStart = vm::checkpoint,
                        onMove = if (viewMode) null else { it -> vm.updateItem(it) },
                        onMoveMosaic = { dx, dy, scale ->
                            vm.changeCollage(record = false) { c ->
                                c.copy(mosaic = c.mosaic.copy(x = c.mosaic.x + dx, y = c.mosaic.y + dy, w = (c.mosaic.w * scale).coerceIn(0.2f, 6f)))
                            }
                        },
                        zoom = zoom,
                        pan = pan,
                        onView = { z, p -> zoom = z; pan = p },
                        onOpen = if (viewMode) { it -> opened = it } else null,
                    )
                }
                Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tyingFrom != null) {
                        BoardChip("Tap what to tie it to · Cancel", YARN, Color.White, "collage-tying") { tyingFrom = null }
                    }
                    if (zoom != 1f || pan != Offset.Zero) {
                        BoardChip("${(zoom * 100).roundToInt()}% · Back to start", cs.inverseSurface, cs.inverseOnSurface, "collage-view-reset") { zoom = 1f; pan = Offset.Zero }
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (viewMode) {
                    ToolGroup(cs.primaryContainer, cs.onPrimaryContainer) {
                        LabeledTool(Icons.Filled.Edit, "Back to editing", "collage-edit-mode") { viewMode = false }
                    }
                } else {
                // Add things: one popup.
                ToolGroup(cs.primaryContainer, cs.onPrimaryContainer) {
                    ToolPopup(Icons.Filled.Add, "Add to the board", "collage-add") { close ->
                        PopupSection(if (collage.mosaic.on) "Photo · joins the mosaic" else "Photo") {}
                        ActionRow(Icons.Filled.PhotoCamera, "Take a photo", "collage-camera") { close(); photos.camera() }
                        ActionRow(Icons.Filled.PhotoLibrary, "From gallery", "collage-photo") { close(); photos.gallery() }
                        PopupSection("Saved") {}
                        ActionRow(Icons.Filled.Brush, "A sketch", "collage-sketch") { close(); pickIdeas = IdeaKind.SKETCH; picking = CollageKind.IDEA }
                        ActionRow(Icons.Filled.Collections, "Another collage", "collage-idea") { close(); pickIdeas = IdeaKind.COLLAGE; picking = CollageKind.IDEA }
                        ActionRow(Icons.Filled.Checkroom, "A design", "collage-design") { close(); picking = CollageKind.DESIGN }
                        PopupSection("Write") {}
                        ActionRow(Icons.Filled.TextFields, "A sticky note", "collage-text") { close(); editingText = CollageItem(id = "", kind = CollageKind.TEXT) }
                    }
                }
                // The picked item: one popup with everything it can do.
                if (selectedItem != null) {
                    ToolGroup(cs.secondaryContainer, cs.onSecondaryContainer) {
                        ToolPopup(
                            Icons.Filled.Tune, "Picked item", "collage-selected",
                            accent = if (selectedItem.kind == CollageKind.TEXT) Color(selectedItem.color) else null,
                        ) { close ->
                            if (selectedItem.kind == CollageKind.TEXT) {
                                PopupSection("Note size · or pinch it") {
                                    val sizes = listOf("Small" to 0.2f, "Medium" to 0.3f, "Large" to 0.45f, "Huge" to 0.7f)
                                    ChoiceChips(sizes.map { it.first }, sizes.indexOfFirst { it.second == selectedItem.w }, "note-size") { i ->
                                        vm.updateItem(selectedItem.copy(w = sizes[i].second), record = true)
                                    }
                                }
                                PopupSection("Note colour") {
                                    ColourDots(NOTE_COLOURS, selectedItem.color, "note-colour") { c ->
                                        vm.updateItem(selectedItem.copy(color = c), record = true)
                                    }
                                }
                                ActionRow(Icons.Filled.Edit, "Edit note", "collage-edit") { close(); editingText = selectedItem }
                            }
                            val tied = collage.links.count { it.from == selectedItem.id || it.to == selectedItem.id }
                            ActionRow(Icons.Filled.Timeline, "Tie red yarn to…", "collage-link") { close(); tyingFrom = selectedItem.id }
                            if (tied > 0) {
                                ActionRow(Icons.Filled.ContentCut, "Cut its yarn ($tied)", "collage-unlink") {
                                    vm.changeCollage { c -> c.copy(links = c.links.filter { it.from != selectedItem.id && it.to != selectedItem.id }) }
                                }
                            }
                            if (selectedItem.kind == CollageKind.PHOTO && collage.mosaic.on) {
                                ToggleRow("In the mosaic", "Off lets it sit anywhere", selectedItem.inMosaic, "collage-in-mosaic") { on ->
                                    vm.updateItem(selectedItem.copy(inMosaic = on, rot = 0f), record = true)
                                }
                            }
                            if (selectedItem.kind != CollageKind.PHOTO || !selectedItem.inMosaic || !collage.mosaic.on) {
                                ActionRow(Icons.Filled.Crop, "Straighten (turn to 0°)", "collage-straight", enabled = selectedItem.rot != 0f) {
                                    vm.updateItem(selectedItem.copy(rot = 0f), record = true)
                                }
                            }
                            ActionRow(Icons.Filled.FlipToFront, if (selectedItem.inMosaic && collage.mosaic.on) "Move to the end" else "Bring to front", "collage-front") {
                                vm.changeCollage { c -> c.copy(items = c.items.filter { it.id != selectedItem.id } + selectedItem) }
                            }
                            ActionRow(Icons.Filled.FlipToBack, if (selectedItem.inMosaic && collage.mosaic.on) "Move to the start" else "Send to back", "collage-back") {
                                vm.changeCollage { c -> c.copy(items = listOf(selectedItem) + c.items.filter { it.id != selectedItem.id }) }
                            }
                            ActionRow(Icons.Filled.Delete, "Remove from board", "collage-remove") {
                                close()
                                vm.changeCollage { Board.remove(it, selectedItem.id) }
                                selected = null
                            }
                        }
                    }
                }
                // The board: paper and the mosaic.
                ToolGroup(cs.tertiaryContainer, cs.onTertiaryContainer) {
                    ToolPopup(Icons.Filled.Palette, "Board", "collage-paper", accent = Color(collage.paper ?: 0xFFF6EFDF)) { _ ->
                        PopupSection("Paper") {
                            ColourDots(BOARD_PAPERS.map { it.second }, collage.paper ?: 0xFFF6EFDF, "board") { c ->
                                vm.changeCollage { it.copy(paper = c) }
                            }
                        }
                    }
                    ToolPopup(Icons.Filled.GridView, "Mosaic", "collage-mosaic", selected = collage.mosaic.on) { _ ->
                        val m = collage.mosaic
                        ToggleRow("Mosaic for photos", "Photos line up in a grid; drag one to move the whole grid", m.on, "collage-mosaic-on") { on ->
                            vm.changeCollage { Board.setMosaic(it, on) }
                        }
                        PopupSection("Columns") {
                            val cols = listOf(2, 3, 4, 5, 6)
                            ChoiceChips(cols.map { "$it" }, cols.indexOf(m.columns), "collage-mosaic-cols") { i ->
                                vm.changeCollage { c -> Board.setMosaic(c, true).let { it.copy(mosaic = it.mosaic.copy(columns = cols[i])) } }
                            }
                        }
                        PopupSection("Gap") {
                            val gaps = listOf("None" to 0f, "Thin" to 0.01f, "Wide" to 0.03f)
                            ChoiceChips(gaps.map { it.first }, gaps.indexOfFirst { it.second == m.gap }, "collage-mosaic-gap") { i ->
                                vm.changeCollage { c -> Board.setMosaic(c, true).let { it.copy(mosaic = it.mosaic.copy(gap = gaps[i].second)) } }
                            }
                        }
                        PopupSection("Tiles") {
                            val shapes = listOf("Square" to 1f, "Tall" to 4f / 3f, "Wide" to 3f / 4f)
                            ChoiceChips(shapes.map { it.first }, shapes.indexOfFirst { it.second == m.shape }, "collage-mosaic-shape") { i ->
                                vm.changeCollage { c -> Board.setMosaic(c, true).let { it.copy(mosaic = it.mosaic.copy(shape = shapes[i].second)) } }
                            }
                        }
                    }
                    ToolButton(Icons.Filled.Visibility, "View mode: tap to see things large", "collage-view-mode") {
                        viewMode = true; selected = null; tyingFrom = null
                    }
                    ToolButton(Icons.AutoMirrored.Filled.Undo, "Undo", "collage-undo", enabled = draft.canUndo) { vm.undo() }
                    ToolButton(Icons.AutoMirrored.Filled.Redo, "Redo", "collage-redo", enabled = draft.canRedo) { vm.redo() }
                }
                }
            }
            if (collage.isEmpty) {
                Text(
                    "Add photos, sketches, designs and sticky notes. Drag the paper to move around, pinch to zoom; drag things with one finger, pinch to resize and turn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).navigationBarsPadding(),
                )
            }
        }
    }

    opened?.let { item ->
        // Follow the saved item so pins added in the viewer show straight away.
        val live = collage.items.firstOrNull { it.id == item.id } ?: item
        CollageItemViewer(live, ideas, designs, onChange = { vm.updateItem(it, record = true) }) { opened = null }
    }

    picking?.let { kind ->
        val isIdea = kind == CollageKind.IDEA
        PickerDialog(
            title = when {
                !isIdea -> "Add a design"
                pickIdeas == IdeaKind.SKETCH -> "Add a sketch"
                else -> "Add a collage"
            },
            entries = if (isIdea) {
                ideas.filter { it.id != draft.idea.id && it.kind == pickIdeas }.map { it.id to it.title.ifBlank { if (it.kind == IdeaKind.SKETCH) "Sketch" else "Collage" } }
            } else designs.map { it.id to it.name.ifBlank { "Untitled design" } },
            empty = when {
                !isIdea -> "No designs yet."
                pickIdeas == IdeaKind.SKETCH -> "No sketches yet. Make one from the Sketch board's + button."
                else -> "No other collages yet."
            },
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
