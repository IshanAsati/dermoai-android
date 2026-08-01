package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.DailyCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyCheckInDao {

    @Query("SELECT * FROM daily_check_ins WHERE userId = :userId AND dateKey = :dateKey LIMIT 1")
    suspend fun getByDate(userId: String, dateKey: String): DailyCheckInEntity?

    @Query("SELECT * FROM daily_check_ins WHERE userId = :userId ORDER BY dateKey DESC")
    fun observeByUserId(userId: String): Flow<List<DailyCheckInEntity>>

    @Query("SELECT COUNT(DISTINCT dateKey) FROM daily_check_ins WHERE userId = :userId")
    fun observeTotalDays(userId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkIn: DailyCheckInEntity)

    @Query("DELETE FROM daily_check_ins WHERE id = :id")
    suspend fun deleteById(id: String)
}
