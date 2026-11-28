package com.boro.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    /** Plain text of the note, for search and previews. */
    val body: String = "",
    /** The formatted note as JSON (see [com.boro.notes.notes.NoteDoc]); null for notes from before formatting. */
    val content: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** When it was moved to the Trash; null while it's a live note. */
    val deletedAt: Long? = null,
)
