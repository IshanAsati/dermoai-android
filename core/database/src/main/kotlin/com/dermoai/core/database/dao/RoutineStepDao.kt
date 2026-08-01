package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.RoutineStepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineStepDao {

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY sortOrder ASC")
    fun observeByRoutineId(routineId: String): Flow<List<RoutineStepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(step: RoutineStepEntity)

    @Query("DELETE FROM routine_steps WHERE id = :stepId")
    suspend fun deleteById(stepId: String)

    @Query("DELETE FROM routine_steps WHERE routineId = :routineId")
    suspend fun deleteByRoutineId(routineId: String)
}
