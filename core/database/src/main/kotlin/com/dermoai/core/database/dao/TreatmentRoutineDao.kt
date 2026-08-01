package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.TreatmentRoutineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentRoutineDao {

    @Query("SELECT * FROM treatment_routines WHERE userId = :userId ORDER BY createdAt ASC")
    fun observeByUserId(userId: String): Flow<List<TreatmentRoutineEntity>>

    @Query("SELECT * FROM treatment_routines WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TreatmentRoutineEntity?

    @Query("SELECT * FROM treatment_routines WHERE userId = :userId ORDER BY createdAt ASC LIMIT 1")
    suspend fun getLatestByUserId(userId: String): TreatmentRoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routine: TreatmentRoutineEntity)

    @Query("DELETE FROM treatment_routines WHERE id = :id")
    suspend fun deleteById(id: String)
}
