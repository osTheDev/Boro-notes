package com.boro.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boro.notes.ui.NotesScreen
import com.boro.notes.ui.NotesViewModel
import com.boro.notes.ui.theme.BoroTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as BoroApp).repository
        setContent {
            BoroTheme {
                val vm: NotesViewModel = viewModel(factory = NotesViewModel.factory(repository))
                NotesScreen(vm)
            }
        }
    }
}
