package com.boro.notes

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boro.notes.data.NoteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Checkpoint 1 installs have a version 1 database; upgrading must keep their notes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun version1NotesSurviveUpgrade() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO notes (title, body, createdAt, updatedAt) VALUES ('Kept', 'from v1', 1, 1)")
            db.version = 1
        }

        val database = NoteDatabase.create(context, name)
        try {
            val notes = database.noteDao().observe("").first()
            assertEquals(listOf("Kept"), notes.map { it.title })
            assertEquals(0, database.designDao().observeAll().first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun version2NotesAndDesignsSurviveUpgrade() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test-v2.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `designs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `stage` TEXT NOT NULL, `noteId` INTEGER, `shapes` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO notes (title, body, createdAt, updatedAt) VALUES ('Pocket ideas', 'scoop pocket', 1, 1)")
            db.execSQL("INSERT INTO designs (name, stage, noteId, shapes, createdAt, updatedAt) VALUES ('Jeans', 'IDEA', 1, '[]', 1, 1)")
            db.version = 2
        }

        val database = NoteDatabase.create(context, name)
        try {
            val notes = database.noteDao().observe("").first()
            assertEquals(listOf("Pocket ideas"), notes.map { it.title })
            assertEquals("scoop pocket", notes.single().body)
            assertEquals(null, notes.single().content)
            assertEquals(listOf("Jeans"), database.designDao().observeAll().first().map { it.name })
        } finally {
            database.close()
        }
    }

    @Test
    fun version3UpgradeAddsEmptyTasks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test-v3.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `content` TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `designs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `stage` TEXT NOT NULL, `noteId` INTEGER, `shapes` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO notes (title, body, createdAt, updatedAt, content) VALUES ('Rich', 'text', 1, 1, '{\"blocks\":[]}')")
            db.version = 3
        }

        val database = NoteDatabase.create(context, name)
        try {
            val note = database.noteDao().observe("").first().single()
            assertEquals("Rich", note.title)
            assertEquals("{\"blocks\":[]}", note.content)
            val tasks = database.taskDao()
            assertEquals(0, tasks.observeHabits().first().size)
            val id = tasks.insertHabit(com.boro.notes.data.Habit(name = "Stretch"))
            tasks.insertCheck(com.boro.notes.data.HabitCheck(id, 20_000))
            tasks.insertCheck(com.boro.notes.data.HabitCheck(id, 20_000))
            assertEquals(1, tasks.observeChecks(19_999, 20_001).first().size)
            tasks.deleteHabit(id)
            assertEquals(0, tasks.observeChecks(0, 99_999).first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun version4UpgradeAddsJournalsAndTrash() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test-v4.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `content` TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `designs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `stage` TEXT NOT NULL, `noteId` INTEGER, `shapes` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `target` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS `habit_checks` (`habitId` INTEGER NOT NULL, `day` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `day`))")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`text` TEXT NOT NULL, `done` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `doneAt` INTEGER)"
            )
            db.execSQL("INSERT INTO notes (title, body, createdAt, updatedAt, content) VALUES ('Kept', 'text', 1, 1, NULL)")
            db.execSQL("INSERT INTO designs (name, stage, noteId, shapes, createdAt, updatedAt) VALUES ('Coat', 'IDEA', NULL, '[]', 1, 1)")
            db.execSQL("INSERT INTO todos (text, done, position, createdAt, doneAt) VALUES ('Buy thread', 0, 0, 1, NULL)")
            db.version = 4
        }

        val database = NoteDatabase.create(context, name)
        try {
            val note = database.noteDao().observe("").first().single()
            assertEquals("Kept", note.title)
            assertEquals(null, note.deletedAt)
            assertEquals(listOf("Coat"), database.designDao().observeAll().first().map { it.name })
            assertEquals(listOf("Buy thread"), database.taskDao().observeTodos().first().map { it.text })

            // Trash hides a note until it's restored; old trash is purged.
            database.noteDao().setDeleted(note.id, 10)
            assertEquals(0, database.noteDao().observe("").first().size)
            assertEquals(1, database.noteDao().observeTrash().first().size)
            database.noteDao().purge(5)
            assertEquals(1, database.noteDao().observeTrash().first().size)
            database.noteDao().purge(11)
            assertEquals(0, database.noteDao().observeTrash().first().size)

            val journals = com.boro.notes.data.RoomJournalRepository(database.journalDao())
            val id = journals.createJournal("Field guide", 2, 1)
            val page = journals.createPage(id, 20_000)
            journals.savePage(page, "Moss", "green", null)
            assertEquals(listOf("Moss"), journals.observePages(id).first().map { it.title })
            journals.trashJournal(id)
            assertEquals(0, journals.observeJournals().first().size)
            journals.purgeTrash(Long.MAX_VALUE)
            assertEquals(0, journals.observeTrashedJournals().first().size)
            assertEquals(null, journals.getPage(page))
        } finally {
            database.close()
        }
    }

    @Test
    fun version5UpgradeKeepsPagesAndAddsChapters() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test-v5.db"
        context.deleteDatabase(name)
        // Build a version 5 database through Room's own v5 schema: a v4 file upgraded once.
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `body` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `content` TEXT, `deletedAt` INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `designs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `stage` TEXT NOT NULL, `noteId` INTEGER, `shapes` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `target` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS `habit_checks` (`habitId` INTEGER NOT NULL, `day` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `day`))")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`text` TEXT NOT NULL, `done` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `doneAt` INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `journals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `color` INTEGER NOT NULL, `pattern` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `journal_pages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`journalId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, " +
                    "`content` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER)"
            )
            db.execSQL("INSERT INTO journals (title, color, pattern, createdAt, updatedAt, deletedAt) VALUES ('Days', 1, 2, 1, 1, NULL)")
            db.execSQL("INSERT INTO journal_pages (journalId, day, title, body, content, createdAt, updatedAt, deletedAt) VALUES (1, 20000, 'Moss walk', 'green', NULL, 1, 1, NULL)")
            db.version = 5
        }

        val database = NoteDatabase.create(context, name)
        try {
            val journals = com.boro.notes.data.RoomJournalRepository(database.journalDao())
            val page = journals.observePages(1).first().single()
            assertEquals("Moss walk", page.title)
            assertEquals(null, page.chapter)
            journals.setChapter(page.id, "Spring")
            assertEquals("Spring", journals.getPage(page.id)?.chapter)
            // v7: a book-wide page paper, unset (journal default) on existing books.
            val book = journals.getJournal(1)!!
            assertEquals(null, book.paper)
            journals.updateJournal(book.copy(paper = "{}"))
            assertEquals("{}", journals.getJournal(1)?.paper)
            // v8: the savings list.
            val tasks = com.boro.notes.data.RoomTaskRepository(database.taskDao())
            tasks.addWish("Coat", 12_000)
            assertEquals(listOf("Coat"), tasks.observeWishes().first().map { it.name })
            // v9: projects with dated photos, and the Sketch board's ideas.
            val projects = com.boro.notes.data.RoomProjectRepository(database.projectDao())
            val projectId = projects.create(com.boro.notes.data.Project(name = "Crane jeans"))
            projects.addPhoto(projectId, "knee.jpg")
            assertEquals(listOf("Crane jeans"), projects.observeProjects().first().map { it.name })
            assertEquals(listOf("knee.jpg"), projects.observePhotos(projectId).first().map { it.file })
            assertEquals(com.boro.notes.data.ProjectStep.DESIGN, projects.get(projectId)!!.step)
            val ideas = com.boro.notes.data.RoomIdeaRepository(database.ideaDao())
            ideas.create(com.boro.notes.data.Idea(title = "Scoop", collection = "Pockets"))
            assertEquals(listOf("Pockets"), ideas.observeIdeas().first().map { it.collection })
        } finally {
            database.close()
        }
    }
}
