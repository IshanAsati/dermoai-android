package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.ScanPredictionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanPredictionDao {

    @Query("SELECT * FROM scan_predictions WHERE scanId = :scanId ORDER BY rank ASC")
    fun observeByScanId(scanId: String): Flow<List<ScanPredictionEntity>>

    @Query("SELECT * FROM scan_predictions WHERE scanId = :scanId ORDER BY rank ASC LIMIT 1")
    suspend fun topPrediction(scanId: String): ScanPredictionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prediction: ScanPredictionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(predictions: List<ScanPredictionEntity>)

    @Query("DELETE FROM scan_predictions WHERE scanId = :scanId")
    suspend fun deleteByScanId(scanId: String)
}
