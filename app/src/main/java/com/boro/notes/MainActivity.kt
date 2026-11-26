package com.boro.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boro.notes.ui.BoroRoot
import com.boro.notes.ui.DesignsViewModel
import com.boro.notes.ui.NotesViewModel
import com.boro.notes.ui.theme.BoroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BoroApp
        setContent {
            BoroTheme {
                val notesVm: NotesViewModel = viewModel(factory = NotesViewModel.factory(app.repository))
                val designsVm: DesignsViewModel = viewModel(factory = DesignsViewModel.factory(app.designs, app.repository))
                BoroRoot(notesVm, designsVm)
            }
        }
    }
}
