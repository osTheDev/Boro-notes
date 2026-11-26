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
}
