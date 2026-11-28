package com.boro.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boro.notes.ui.BoroRoot
import com.boro.notes.ui.DesignsViewModel
import com.boro.notes.ui.NotesViewModel
import com.boro.notes.ui.TasksViewModel
import com.boro.notes.ui.JournalViewModel
import com.boro.notes.ui.TrashViewModel
import com.boro.notes.ui.ProjectsViewModel
import com.boro.notes.ui.IdeasViewModel
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boro.notes.ui.theme.BoroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BoroApp
        setContent {
            val look by app.look.settings.collectAsStateWithLifecycle()
            BoroTheme(look) {
                val notesVm: NotesViewModel = viewModel(factory = NotesViewModel.factory(app.repository))
                val designsVm: DesignsViewModel = viewModel(factory = DesignsViewModel.factory(app.designs, app.repository))
                val tasksVm: TasksViewModel = viewModel(factory = TasksViewModel.factory(app.tasks))
                val journalVm: JournalViewModel = viewModel(factory = JournalViewModel.factory(app.journals))
                val trashVm: TrashViewModel = viewModel(factory = TrashViewModel.factory(app.repository, app.designs, app.journals, app.projects, app.ideas))
                val projectsVm: ProjectsViewModel = viewModel(factory = ProjectsViewModel.factory(app.projects, app.designs, app.ideas))
                val ideasVm: IdeasViewModel = viewModel(factory = IdeasViewModel.factory(app.ideas))
                // Sets the theme's text colour for everything below, so text without its own colour
                // never falls back to black (unreadable in dark mode).
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    BoroRoot(notesVm, designsVm, tasksVm, journalVm, trashVm, projectsVm, ideasVm, look, app.look::update)
                }
            }
        }
    }
}
