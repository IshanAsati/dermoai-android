package com.dermoai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dermoai.core.database.entity.PatientLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientLinkDao {

    @Query("SELECT * FROM patient_links WHERE doctorId = :doctorId ORDER BY linkedAt DESC")
    fun observeByDoctorId(doctorId: String): Flow<List<PatientLinkEntity>>

    /**
     * Active *and* consented. The consent timestamp is part of the filter rather
     * than something callers remember to check, so a row force-set to ACTIVE
     * without the patient ever accepting cannot reach a dashboard through this
     * query.
     */
    @Query(
        "SELECT * FROM patient_links WHERE doctorId = :doctorId AND status = 'ACTIVE' " +
            "AND consentGrantedAt IS NOT NULL ORDER BY linkedAt DESC",
    )
    fun observeActiveByDoctorId(doctorId: String): Flow<List<PatientLinkEntity>>

    /** The patient's own view: every doctor who has, or has had, access to them. */
    @Query("SELECT * FROM patient_links WHERE patientUserId = :patientUserId ORDER BY linkedAt DESC")
    fun observeByPatientUserId(patientUserId: String): Flow<List<PatientLinkEntity>>

    @Query(
        "SELECT * FROM patient_links WHERE patientUserId = :patientUserId " +
            "AND doctorId = :doctorId LIMIT 1",
    )
    suspend fun getByPatientAndDoctor(patientUserId: String, doctorId: String): PatientLinkEntity?

    @Query("SELECT * FROM patient_links WHERE id = :linkId LIMIT 1")
    suspend fun getById(linkId: String): PatientLinkEntity?

    @Query(
        "SELECT COUNT(*) FROM patient_links WHERE doctorId = :doctorId AND status = 'ACTIVE' " +
            "AND consentGrantedAt IS NOT NULL",
    )
    fun observeActiveCount(doctorId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: PatientLinkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(links: List<PatientLinkEntity>)

    /**
     * Status transitions go through their own statement so accepting or revoking
     * consent never has to read-modify-write the whole row. A revoke racing an
     * unrelated profile-name refresh must not be undone by it.
     */
    @Query(
        "UPDATE patient_links SET status = :status, consentGrantedAt = :consentGrantedAt, " +
            "updatedAt = :updatedAt, syncStatus = 'PENDING' WHERE id = :linkId",
    )
    suspend fun updateStatus(
        linkId: String,
        status: String,
        consentGrantedAt: Long?,
        updatedAt: Long,
    )

    /**
     * Hard delete, for the patient's "erase this entirely" path only. Ordinary
     * revocation should use [updateStatus] with REVOKED — the row is what lets the
     * audit trail say access existed and when it ended.
     */
    @Query("DELETE FROM patient_links WHERE id = :linkId")
    suspend fun delete(linkId: String)
}
