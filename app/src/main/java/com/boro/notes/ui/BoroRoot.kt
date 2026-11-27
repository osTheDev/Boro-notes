package com.boro.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TaskAlt
import com.boro.notes.ui.theme.LookSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** A note filling the screen, with a small button that slides the notes list in and out. */
@Composable
private fun FullScreenNote(note: EditorState, vm: NotesViewModel, fromDesign: Boolean) {
    var listOpen by rememberSaveable { mutableStateOf(false) }
    val notes by vm.notes.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    // Back from a design's note returns to the sketch; from Notes it closes the note.
    val back = { if (fromDesign) vm.setFullScreen(false) else vm.close() }
    BackHandler { if (listOpen) listOpen = false else back() }

    Row(Modifier.fillMaxSize()) {
        if (listOpen && !fromDesign) {
            NoteListPane(
                notes = notes,
                query = query,
                selectedId = note.id,
                onQueryChange = vm::onQueryChange,
                onOpen = { vm.open(it) },
                onCreate = vm::createNote,
                modifier = Modifier.width(300.dp).fillMaxHeight().testTag(TAG_LIST_PANE),
            )
            VerticalDivider()
        }
        EditorPane(
            note,
            vm,
            onBack = back,
            showDelete = !fromDesign,
            modifier = Modifier.weight(1f),
            extraActions = {
                if (!fromDesign) {
                    TextButton(onClick = { listOpen = !listOpen }, modifier = Modifier.testTag(TAG_LIST_TOGGLE)) {
                        Text(if (listOpen) "Hide list" else "All notes")
                    }
                }
            },
        )
    }
}

const val TAG_LIST_TOGGLE = "listToggle"

enum class Section(val label: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Filled.Home, "navHome"),
    NOTES("Notes", Icons.Filled.EditNote, "navNotes"),
    DESIGNS("Designs", Icons.Filled.Checkroom, "navDesigns"),
    TASKS("Tasks", Icons.Filled.TaskAlt, "navTasks"),
    JOURNAL("Journal", Icons.Filled.AutoStories, "navJournal"),
}

/** Top level: Home, Notes, Designs and Tasks, with a bottom bar on the cover screen and a side rail on the inner screen. */
@Composable
fun BoroRoot(
    notesVm: NotesViewModel,
    designsVm: DesignsViewModel,
    tasksVm: TasksViewModel,
    journalVm: JournalViewModel,
    trashVm: TrashViewModel,
    look: LookSettings = LookSettings(),
    onLookChange: (LookSettings) -> Unit = {},
) {
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = section != Section.HOME) { section = Section.HOME }
    if (trashOpen) {
        BackHandler { trashOpen = false }
        TrashScreen(trashVm, onBack = { trashOpen = false })
        return
    }
    val bookOpen = journalVm.openId.collectAsStateWithLifecycle().value != null
    val bookView by journalVm.bookView.collectAsStateWithLifecycle()
    val noteOpen = notesVm.editor.collectAsStateWithLifecycle().value != null
    val sketchOpen = designsVm.sketch.collectAsStateWithLifecycle().value != null
    val fullScreen by notesVm.fullScreen.collectAsStateWithLifecycle()
    val openNote = notesVm.editor.collectAsStateWithLifecycle().value

    val sketch by designsVm.sketch.collectAsStateWithLifecycle()

    // A note in full screen covers everything, side menu included. From Designs it must be the
    // design's own note (it may still be loading just after "Notes" is tapped).
    val fullScreenNote = openNote?.takeIf {
        fullScreen && (section == Section.NOTES || it.id == sketch?.noteId)
    }
    if (fullScreenNote != null) {
        FullScreenNote(fullScreenNote, notesVm, fromDesign = section == Section.DESIGNS)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TWO_PANE_MIN_WIDTH
        val content = @Composable { modifier: Modifier ->
            Box(modifier) {
                when (section) {
                    Section.HOME -> {
                        val notes by notesVm.notes.collectAsStateWithLifecycle()
                        val designs by designsVm.all.collectAsStateWithLifecycle()
                        val habits by tasksVm.habits.collectAsStateWithLifecycle()
                        val todos by tasksVm.todos.collectAsStateWithLifecycle()
                        val journals by journalVm.journals.collectAsStateWithLifecycle()
                        val todayIndex = tasksVm.todayDate().dayOfWeek.value - 1
                        HomeScreen(
                            stats = HomeStats(
                                notes = notes.size,
                                designs = designs.size,
                                punchCards = habits.size,
                                punchedToday = if (tasksVm.isThisWeek) habits.count { it.punched[todayIndex] } else 0,
                                openTodos = todos.count { !it.done },
                                journals = journals.size,
                            ),
                            look = look,
                            onLookChange = onLookChange,
                            onOpen = { section = it },
                            onTrash = { trashOpen = true },
                        )
                    }
                    Section.NOTES -> NotesScreen(notesVm)
                    Section.DESIGNS -> DesignsScreen(designsVm, notesVm, wide)
                    Section.TASKS -> TasksScreen(tasksVm, wide)
                    Section.JOURNAL -> JournalScreen(journalVm, wide)
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
              // The journal's book view takes the whole screen, side menu included.
              if (!(section == Section.JOURNAL && bookOpen && bookView)) {
                // A solid panel casting a shadow, so the side menu reads as its own surface
                // and the textured screens sit beside it instead of just stopping.
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .zIndex(1f)
                        .shadow(8.dp),
                ) {
                    Section.entries.forEach { s ->
                        NavigationRailItem(
                            selected = section == s,
                            onClick = { section = s },
                            icon = { Icon(s.icon, contentDescription = null) },
                            label = { Text(s.label) },
                            modifier = Modifier.testTag(s.tag),
                        )
                    }
                }
              }
                content(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            // Full-screen editors hide the bar so the note or canvas gets the whole cover screen.
            val showBar = when (section) {
                Section.NOTES -> !noteOpen
                Section.DESIGNS -> !sketchOpen
                // An open book gets the whole cover screen (closed cover or pages).
                Section.JOURNAL -> !bookOpen
                Section.HOME, Section.TASKS -> true
            }
            Column(Modifier.fillMaxSize()) {
                content(
                    Modifier
                        .weight(1f)
                        .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier)
                )
                if (showBar) {
                    NavigationBar {
                        Section.entries.forEach { s ->
                            NavigationBarItem(
                                selected = section == s,
                                onClick = { section = s },
                                icon = { Icon(s.icon, contentDescription = null) },
                                label = { Text(s.label) },
                                modifier = Modifier.testTag(s.tag),
                            )
                        }
                    }
                }
            }
        }
    }
}
