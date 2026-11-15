package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.DoctorSharePasscodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorShareDao {

    @Query("SELECT * FROM doctor_share_passcodes WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeByUserId(userId: String): Flow<List<DoctorSharePasscodeEntity>>

    @Query("SELECT * FROM doctor_share_passcodes WHERE passcode = :passcode AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveByPasscode(passcode: String): DoctorSharePasscodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(passcodeEntity: DoctorSharePasscodeEntity)

    @Query("UPDATE doctor_share_passcodes SET status = 'REVOKED' WHERE id = :id")
    suspend fun revokePasscode(id: String)
}
