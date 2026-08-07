package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.DoctorProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorProfileDao {

    @Query("SELECT * FROM doctor_profiles WHERE userId = :userId LIMIT 1")
    fun observeByUserId(userId: String): Flow<DoctorProfileEntity?>

    @Query("SELECT * FROM doctor_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): DoctorProfileEntity?

    @Query("SELECT * FROM doctor_profiles WHERE id = :doctorId LIMIT 1")
    suspend fun getById(doctorId: String): DoctorProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: DoctorProfileEntity)

    /**
     * Verification is set by a review process, not by the doctor editing their own
     * profile, so it moves through its own statement — a whole-row upsert from the
     * edit screen would silently carry a stale status back over a fresh decision.
     */
    @Query(
        "UPDATE doctor_profiles SET verificationStatus = :status, verifiedAt = :verifiedAt, " +
            "updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :doctorId",
    )
    suspend fun updateVerification(
        doctorId: String,
        status: String,
        verifiedAt: Long?,
        updatedAt: Long,
    )

    @Query("DELETE FROM doctor_profiles WHERE id = :doctorId")
    suspend fun deleteById(doctorId: String)
}
