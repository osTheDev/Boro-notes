package com.boro.notes

import android.app.Application
import com.boro.notes.data.NoteDatabase
import com.boro.notes.data.NoteRepository
import com.boro.notes.data.RoomNoteRepository

class BoroApp : Application() {
    val repository: NoteRepository by lazy {
        RoomNoteRepository(NoteDatabase.create(this).noteDao())
    }
}
