package com.boro.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
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

enum class Section(val label: String, val icon: ImageVector, val tag: String) {
    NOTES("Notes", Icons.AutoMirrored.Filled.List, "navNotes"),
    DESIGNS("Designs", Icons.Filled.Create, "navDesigns"),
}

/** Top level: Notes and Designs, with a bottom bar on the cover screen and a side rail on the inner screen. */
@Composable
fun BoroRoot(notesVm: NotesViewModel, designsVm: DesignsViewModel) {
    var section by rememberSaveable { mutableStateOf(Section.NOTES) }
    val noteOpen = notesVm.editor.collectAsStateWithLifecycle().value != null
    val sketchOpen = designsVm.sketch.collectAsStateWithLifecycle().value != null
    val fullScreen by notesVm.fullScreen.collectAsStateWithLifecycle()
    val openNote = notesVm.editor.collectAsStateWithLifecycle().value

    // A note in full screen covers everything, side menu included.
    if (fullScreen && openNote != null) {
        BackHandler { notesVm.setFullScreen(false) }
        EditorPane(
            openNote,
            notesVm,
            onBack = { notesVm.setFullScreen(false) },
            showDelete = false,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= TWO_PANE_MIN_WIDTH
        val content = @Composable { modifier: Modifier ->
            Box(modifier) {
                when (section) {
                    Section.NOTES -> NotesScreen(notesVm)
                    Section.DESIGNS -> DesignsScreen(designsVm, notesVm, wide)
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
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
                content(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            // Full-screen editors hide the bar so the note or canvas gets the whole cover screen.
            val showBar = when (section) {
                Section.NOTES -> !noteOpen
                Section.DESIGNS -> !sketchOpen
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
