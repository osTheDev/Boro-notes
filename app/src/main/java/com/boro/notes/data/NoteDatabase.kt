package com.boro.notes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Note::class, Design::class, Habit::class, HabitCheck::class, Todo::class, Journal::class, JournalPage::class, Wish::class], version = 8, exportSchema = true)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun designDao(): DesignDao
    abstract fun taskDao(): TaskDao
    abstract fun journalDao(): JournalDao

    companion object {
        /** Checkpoint 2 added designs (garment sketches). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `designs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`stage` TEXT NOT NULL, " +
                        "`noteId` INTEGER, " +
                        "`shapes` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        /** Checkpoint 4 added formatted note content. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `content` TEXT")
            }
        }

        /** Tasks: weekly punch cards (habits + daily punches) and the to-do list. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `target` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_checks` (`habitId` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitId`, `day`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `todos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, `done` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `doneAt` INTEGER)"
                )
            }
        }

        /** Journals and pages, and a Trash (deletedAt) for notes and designs. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `designs` ADD COLUMN `deletedAt` INTEGER")
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
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `journal_pages` ADD COLUMN `chapter` TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `journals` ADD COLUMN `paper` TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wishes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `price` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `boughtAt` INTEGER)"
                )
            }
        }

        fun create(context: Context, name: String = "boro-notes.db"): NoteDatabase =
            Room.databaseBuilder(context, NoteDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
    }
}
