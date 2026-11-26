package com.boro.notes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DesignDao {
    @Query("SELECT * FROM designs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Design>>

    @Query("SELECT * FROM designs WHERE id = :id")
    suspend fun get(id: Long): Design?

    @Insert
    suspend fun insert(design: Design): Long

    @Update
    suspend fun update(design: Design)

    @Query("DELETE FROM designs WHERE id = :id")
    suspend fun delete(id: Long)
}
