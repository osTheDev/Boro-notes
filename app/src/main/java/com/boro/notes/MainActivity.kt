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
                val trashVm: TrashViewModel = viewModel(factory = TrashViewModel.factory(app.repository, app.designs, app.journals))
                BoroRoot(notesVm, designsVm, tasksVm, journalVm, trashVm, look, app.look::update)
            }
        }
    }
}
