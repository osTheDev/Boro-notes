package com.boro.notes

import android.app.Application
import com.boro.notes.data.JournalRepository
import com.boro.notes.data.RoomJournalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.boro.notes.data.DesignRepository
import com.boro.notes.data.NoteDatabase
import com.boro.notes.data.RoomDesignRepository
import com.boro.notes.data.NoteRepository
import com.boro.notes.data.RoomNoteRepository
import com.boro.notes.data.RoomTaskRepository
import com.boro.notes.data.TaskRepository
import com.boro.notes.ui.theme.LookPrefs

class BoroApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database by lazy { NoteDatabase.create(this) }
    val repository: NoteRepository by lazy { RoomNoteRepository(database.noteDao()) }
    val designs: DesignRepository by lazy { RoomDesignRepository(database.designDao()) }
    val tasks: TaskRepository by lazy { RoomTaskRepository(database.taskDao()) }
    val look: LookPrefs by lazy { LookPrefs(this) }
    val journals: JournalRepository by lazy { RoomJournalRepository(database.journalDao()) }
    val projects: com.boro.notes.data.ProjectRepository by lazy { com.boro.notes.data.RoomProjectRepository(database.projectDao()) }
    val ideas: com.boro.notes.data.IdeaRepository by lazy { com.boro.notes.data.RoomIdeaRepository(database.ideaDao()) }

    override fun onCreate() {
        super.onCreate()
        // Empty anything that has been in the Trash for over a week.
        appScope.launch {
            val before = System.currentTimeMillis() - com.boro.notes.ui.TRASH_DAYS * 24L * 60 * 60 * 1000
            runCatching {
                repository.purgeTrash(before)
                designs.purgeTrash(before)
                journals.purgeTrash(before)
                projects.purgeTrash(before)
                ideas.purgeTrash(before)
            }
        }
    }
}
