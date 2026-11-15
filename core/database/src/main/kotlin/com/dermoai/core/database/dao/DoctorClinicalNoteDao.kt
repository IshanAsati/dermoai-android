package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.DoctorClinicalNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorClinicalNoteDao {

    @Query("SELECT * FROM doctor_clinical_notes WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUserId(userId: String): Flow<List<DoctorClinicalNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DoctorClinicalNoteEntity)

    @Query("DELETE FROM doctor_clinical_notes WHERE id = :id")
    suspend fun deleteById(id: String)
}
