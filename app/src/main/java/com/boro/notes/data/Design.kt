package com.boro.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Where a garment idea is in its development. */
enum class Stage(val label: String) {
    IDEA("Idea"),
    SKETCH("Sketch"),
    PATTERN("Pattern"),
    SAMPLE("Sample"),
    DONE("Done");

    fun next(): Stage = entries.getOrElse(ordinal + 1) { this }
}

@Entity(tableName = "designs")
data class Design(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val stage: Stage = Stage.IDEA,
    /** The note linked to this design, if any. */
    val noteId: Long? = null,
    /** The drawing, as JSON (see [com.boro.notes.sketch.SketchCodec]). */
    val shapes: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
