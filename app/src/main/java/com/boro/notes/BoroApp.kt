package com.boro.notes

import android.app.Application
import com.boro.notes.data.DesignRepository
import com.boro.notes.data.NoteDatabase
import com.boro.notes.data.RoomDesignRepository
import com.boro.notes.data.NoteRepository
import com.boro.notes.data.RoomNoteRepository
import com.boro.notes.data.RoomTaskRepository
import com.boro.notes.data.TaskRepository
import com.boro.notes.ui.theme.LookPrefs

class BoroApp : Application() {
    private val database by lazy { NoteDatabase.create(this) }
    val repository: NoteRepository by lazy { RoomNoteRepository(database.noteDao()) }
    val designs: DesignRepository by lazy { RoomDesignRepository(database.designDao()) }
    val tasks: TaskRepository by lazy { RoomTaskRepository(database.taskDao()) }
    val look: LookPrefs by lazy { LookPrefs(this) }
}
