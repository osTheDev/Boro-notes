package com.boro.notes

import android.app.Application
import com.boro.notes.data.DesignRepository
import com.boro.notes.data.NoteDatabase
import com.boro.notes.data.RoomDesignRepository
import com.boro.notes.data.NoteRepository
import com.boro.notes.data.RoomNoteRepository

class BoroApp : Application() {
    private val database by lazy { NoteDatabase.create(this) }
    val repository: NoteRepository by lazy { RoomNoteRepository(database.noteDao()) }
    val designs: DesignRepository by lazy { RoomDesignRepository(database.designDao()) }
}
