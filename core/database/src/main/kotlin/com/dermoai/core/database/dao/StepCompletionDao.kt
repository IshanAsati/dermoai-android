package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.StepCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StepCompletionDao {

    @Query("SELECT * FROM step_completions WHERE routineId = :routineId AND dateKey = :dateKey")
    fun observeByRoutineAndDate(routineId: String, dateKey: String): Flow<List<StepCompletionEntity>>

    @Query("SELECT * FROM step_completions WHERE stepId = :stepId AND dateKey = :dateKey LIMIT 1")
    suspend fun getByStepAndDate(stepId: String, dateKey: String): StepCompletionEntity?

    @Query("SELECT COUNT(DISTINCT dateKey) FROM step_completions WHERE routineId = :routineId")
    fun observeCompletionCount(routineId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: StepCompletionEntity)

    @Query("DELETE FROM step_completions WHERE stepId = :stepId AND dateKey = :dateKey")
    suspend fun deleteByStepAndDate(stepId: String, dateKey: String)
}
