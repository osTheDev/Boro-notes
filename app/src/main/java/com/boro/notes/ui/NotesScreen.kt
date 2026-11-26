package com.boro.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boro.notes.data.Note

/** Widths at or above this (the Fold's inner screen) show the list and the open note side by side. */
val TWO_PANE_MIN_WIDTH = 600.dp

@Composable
fun NotesScreen(vm: NotesViewModel) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TWO_PANE_MIN_WIDTH
        val list = @Composable { modifier: Modifier ->
            NoteListPane(
                notes = notes,
                query = query,
                selectedId = editor?.id,
                onQueryChange = vm::onQueryChange,
                // On the inner screen notes open full screen; the list slides in from a button.
                onOpen = { id -> vm.open(id); if (wide) vm.setFullScreen(true) },
                onCreate = { vm.createNote(); if (wide) vm.setFullScreen(true) },
                modifier = modifier,
            )
        }
        val current = editor

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.weight(0.4f).testTag(TAG_LIST_PANE))
                VerticalDivider()
                Box(Modifier.weight(0.6f).fillMaxSize()) {
                    if (current != null) {
                        EditorPane(current, vm, onBack = null, onExpand = { vm.setFullScreen(true) })
                    } else {
                        EmptyDetail()
                    }
                }
            }
        } else if (current == null) {
            list(Modifier.fillMaxSize().testTag(TAG_LIST_PANE))
        } else {
            BackHandler { vm.close() }
            EditorPane(current, vm, onBack = vm::close)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteListPane(
    notes: List<Note>,
    query: String,
    selectedId: Long?,
    onQueryChange: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Boro Notes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, modifier = Modifier.testTag(TAG_NEW_NOTE)) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(TAG_SEARCH),
            )
            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "No notes yet. Tap + to start one." else "No matches",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(notes, key = { it.id }) { note ->
                        NoteRow(note, selected = note.id == selectedId, onClick = { onOpen(note.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, selected: Boolean, onClick: () -> Unit) {
    val supporting: (@Composable () -> Unit)? = if (note.body.isNotBlank()) {
        { Text(note.body, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    } else {
        null
    }
    ListItem(
        headlineContent = {
            Text(note.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = supporting,
        colors = if (selected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else ListItemDefaults.colors(),
        modifier = Modifier.clickable(onClick = onClick).testTag("$TAG_NOTE_PREFIX${note.id}"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorPane(
    state: EditorState,
    vm: NotesViewModel,
    onBack: (() -> Unit)?,
    showDelete: Boolean = true,
    modifier: Modifier = Modifier,
    /** When set, shows a Full screen button. */
    onExpand: (() -> Unit)? = null,
    /** Extra buttons at the start of the top bar's actions. */
    extraActions: @Composable () -> Unit = {},
) {
    val plain = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
    Scaffold(
        modifier = modifier.testTag(TAG_EDITOR_PANE),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_BACK)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    extraActions()
                    if (onExpand != null) {
                        TextButton(onClick = onExpand, modifier = Modifier.testTag(TAG_EXPAND)) { Text("Full screen") }
                    }
                    if (showDelete) {
                        IconButton(onClick = vm::deleteOpenNote, modifier = Modifier.testTag(TAG_DELETE)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete note")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = state.title,
                onValueChange = vm::onTitleChange,
                placeholder = { Text("Title") },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                colors = plain,
                modifier = Modifier.fillMaxWidth().testTag(TAG_TITLE),
            )
            TextField(
                value = state.body,
                onValueChange = vm::onBodyChange,
                placeholder = { Text("Start writing") },
                colors = plain,
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(TAG_BODY),
            )
        }
    }
}

@Composable
private fun EmptyDetail() {
    Box(Modifier.fillMaxSize().testTag(TAG_EMPTY_DETAIL), contentAlignment = Alignment.Center) {
        Text("Pick a note or start a new one", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

const val TAG_LIST_PANE = "listPane"
const val TAG_EDITOR_PANE = "editorPane"
const val TAG_EMPTY_DETAIL = "emptyDetail"
const val TAG_NEW_NOTE = "newNote"
const val TAG_SEARCH = "search"
const val TAG_TITLE = "titleField"
const val TAG_BODY = "bodyField"
const val TAG_BACK = "back"
const val TAG_DELETE = "delete"
const val TAG_EXPAND = "expandNote"
const val TAG_NOTE_PREFIX = "note-"
