package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.DoctorInviteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorInviteDao {

    @Query("SELECT * FROM doctor_invites WHERE doctorId = :doctorId ORDER BY createdAt DESC")
    fun observeByDoctorId(doctorId: String): Flow<List<DoctorInviteEntity>>

    /**
     * Redemption lookup. Deliberately returns expired, spent and revoked rows:
     * the difference between "no such code" and "that code expired last week" is
     * the difference between a patient retyping forever and a patient asking for
     * a new one, and only the caller has the clock to decide.
     * Compare against `code` already uppercased by the caller.
     */
    @Query("SELECT * FROM doctor_invites WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): DoctorInviteEntity?

    @Query("SELECT * FROM doctor_invites WHERE id = :inviteId LIMIT 1")
    suspend fun getById(inviteId: String): DoctorInviteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(invite: DoctorInviteEntity)

    /**
     * Increments in SQL rather than read-then-write, and re-checks the cap in the
     * WHERE clause. Two patients redeeming a single-use code at the same moment
     * would both read `usedCount = 0` in Kotlin and both be admitted; here the
     * second UPDATE matches no rows and returns 0, which the caller must treat as
     * a failed redemption.
     *
     * @return rows updated: 1 on success, 0 if the invite was already spent,
     *   expired or revoked.
     */
    @Query(
        "UPDATE doctor_invites SET usedCount = usedCount + 1, updatedAt = :updatedAt, " +
            "syncStatus = 'PENDING' WHERE id = :inviteId AND revoked = 0 " +
            "AND usedCount < maxUses AND expiresAt > :now",
    )
    suspend fun incrementUse(inviteId: String, now: Long, updatedAt: Long): Int

    @Query(
        "UPDATE doctor_invites SET revoked = 1, updatedAt = :updatedAt, " +
            "syncStatus = 'PENDING' WHERE id = :inviteId",
    )
    suspend fun revoke(inviteId: String, updatedAt: Long)

    @Query("DELETE FROM doctor_invites WHERE id = :inviteId")
    suspend fun deleteById(inviteId: String)
}
