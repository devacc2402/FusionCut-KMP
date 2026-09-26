package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.Layer
import kotlinx.coroutines.flow.Flow

@Dao
interface LayerDao {
    @Query("SELECT * FROM layers WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getLayersForProject(projectId: Long): Flow<List<Layer>>

    @Query("SELECT * FROM layers WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getLayersDirect(projectId: Long): List<Layer>

    @Query("SELECT * FROM layers WHERE id = :id LIMIT 1")
    suspend fun getLayerById(id: String): Layer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayer(layer: Layer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayers(layers: List<Layer>)

    @Update
    suspend fun updateLayer(layer: Layer)

    @Delete
    suspend fun deleteLayer(layer: Layer)

    @Query("DELETE FROM layers WHERE id = :layerId")
    suspend fun deleteLayerById(layerId: String)

    @Query("DELETE FROM layers WHERE projectId = :projectId")
    suspend fun deleteAllLayersForProject(projectId: Long)

    @androidx.room.Transaction
    suspend fun replaceLayersForProject(projectId: Long, layers: List<Layer>) {
        deleteAllLayersForProject(projectId)
        insertLayers(layers)
    }
}
