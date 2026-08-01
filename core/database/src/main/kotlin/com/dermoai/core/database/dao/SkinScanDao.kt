package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.SkinScanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SkinScanDao {

    @Query("SELECT * FROM skin_scans WHERE userId = :userId ORDER BY capturedAt DESC")
    fun observeByUserId(userId: String): Flow<List<SkinScanEntity>>

    @Query("SELECT * FROM skin_scans WHERE userId = :userId ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestByUserId(userId: String): SkinScanEntity?

    @Query("SELECT * FROM skin_scans WHERE id = :scanId LIMIT 1")
    suspend fun getById(scanId: String): SkinScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scan: SkinScanEntity)

    @Query("DELETE FROM skin_scans WHERE id = :scanId")
    suspend fun deleteById(scanId: String)
}
