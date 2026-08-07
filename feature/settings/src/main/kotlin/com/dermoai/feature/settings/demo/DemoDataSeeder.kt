package com.dermoai.feature.settings.demo

import android.content.Context
import com.dermoai.core.common.dispatcher.DispatcherProvider
import com.dermoai.core.database.dao.DoctorProfileDao
import com.dermoai.core.database.dao.PatientLinkDao
import com.dermoai.core.database.dao.ScanPredictionDao
import com.dermoai.core.database.dao.SkinScanDao
import com.dermoai.core.database.dao.UserProfileDao
import com.dermoai.core.database.dao.UserProfileDetailsDao
import com.dermoai.core.database.entity.DoctorProfileEntity
import com.dermoai.core.database.entity.PatientLinkEntity
import com.dermoai.core.database.entity.ScanPredictionEntity
import com.dermoai.core.database.entity.SkinScanEntity
import com.dermoai.core.database.entity.UserProfileDetailsEntity
import com.dermoai.core.domain.model.UserRole
import com.dermoai.core.domain.model.VerificationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** Which surface [DemoDataSeeder.seed] decided to populate, based on the signed-in account's role. */
enum class DemoSeedMode { DOCTOR, PATIENT }

/** What got written, so the caller can show the presenter something more useful than "Done". */
data class DemoSeedResult(
    val mode: DemoSeedMode,
    /** Patients linked (doctor mode) or 1 (patient mode, for the account's own profile). */
    val peopleSeeded: Int,
    val scansSeeded: Int,
)

/**
 * Populates the signed-in account with realistic-looking demo data — for the
 * competition walkthrough tomorrow, not for shipping.
 *
 * **DEBUG-only by construction.** This class has one call site
 * ([com.dermoai.feature.settings.SettingsViewModel.loadDemoData]), reached
 * from a button in [com.dermoai.feature.settings.SettingsScreen] that only
 * exists when `BuildConfig.DEBUG` is true — the same gate
 * `DoctorSessionViewModel.approveOwnClaimForDebug` uses for its own
 * demo-only shortcut. Nothing in this file checks `BuildConfig` itself; it
 * relies on the caller not existing in a release build, which is why the
 * class stays out of any code path a release build can reach.
 *
 * Writes exclusively through the existing Room DAOs — never raw SQL — so the
 * same invariants a real sign-up or scan enforces (the unique
 * `(doctorId, patientUserId)` index on `patient_links`, `REPLACE` upserts on
 * every table) apply here too, and so a device that later gets Appwrite sync
 * configured marks these rows `PENDING` exactly like anything else written
 * locally.
 *
 * **Idempotent.** Every row uses a deterministic id derived from the
 * signed-in account's own id plus a fixed index (see [shortId]), so tapping
 * the seed button twice overwrites the same rows via `OnConflictStrategy.REPLACE`
 * rather than accumulating duplicates. Scan timestamps are computed relative
 * to "now" at seed time, so re-seeding the night before a demo refreshes the
 * whole timeline to still look recent rather than drifting further into the
 * past with each real day that passes.
 *
 * **What is real vs. fake here:** the doctor/patient profile fields, patient
 * links, scans, and predictions are written through the same tables and DAOs
 * the real sign-up, invite-redemption, and scan-capture flows use, with
 * label/code/severity triples lifted from the model's actual taxonomy
 * ([DemoDataPlan]) — every screen that reads this data cannot tell it apart
 * from a real user's. The photo is not a real clinical image — sourcing or
 * fabricating one was out of scope and would be misleading — but it is a
 * real, decodable JPEG file: [DemoPhotoGenerator] procedurally renders a
 * skin-tone macro shot with a lesion-like blob scaled to the scan's top
 * severity, written to the same `filesDir/photos/` directory
 * [ScanScreens.kt][com.dermoai.feature.scan] uses for real captures, so
 * every screen that decodes `imagePath` renders an actual thumbnail instead
 * of falling back to the neutral placeholder tile.
 */
class DemoDataSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val userProfileDao: UserProfileDao,
    private val userProfileDetailsDao: UserProfileDetailsDao,
    private val doctorProfileDao: DoctorProfileDao,
    private val patientLinkDao: PatientLinkDao,
    private val skinScanDao: SkinScanDao,
    private val scanPredictionDao: ScanPredictionDao,
) {

    /**
     * Seeds the account currently signed in on this device.
     *
     * Branches on the persisted [UserRole] (`user_profiles.role`), not on
     * [com.dermoai.core.domain.model.AuthUser.role] — the latter is only a
     * reliable signal in local auth mode; the stored row is what every other
     * doctor-dashboard read path already trusts (see
     * `FirebaseAuthRepository.persistSession`'s comment on why).
     *
     * Runs on [DispatcherProvider.io]: this does real file I/O (rendering and
     * writing a JPEG per scan), which has no business running on
     * `viewModelScope`'s default `Main` dispatcher.
     */
    suspend fun seed(userId: String): DemoSeedResult = withContext(dispatchers.io) {
        val profile = userProfileDao.getById(userId)
        val now = System.currentTimeMillis()
        when (UserRole.fromStorage(profile?.role)) {
            UserRole.DOCTOR -> seedDoctor(userId, now)
            UserRole.PATIENT -> seedPatient(userId, now)
        }
    }

    private suspend fun seedDoctor(userId: String, now: Long): DemoSeedResult {
        val suffix = shortId(userId)
        val existing = doctorProfileDao.getByUserId(userId)
        val doctorId = existing?.id ?: "demo_doctor_profile_$suffix"

        doctorProfileDao.upsert(
            DoctorProfileEntity(
                id = doctorId,
                userId = userId,
                fullName = existing?.fullName.orDefault("Dr. Aditi Rao"),
                qualifications = existing?.qualifications.orDefault(
                    DoctorProfileEntity.encodeQualifications(listOf("MBBS", "MD Dermatology")),
                ),
                registrationNumber = existing?.registrationNumber.orDefault("MCI-DEMO-48213"),
                specialty = existing?.specialty.orDefault("Dermatology"),
                institution = existing?.institution.orDefault("Sunrise Skin & Wellness Clinic"),
                yearsExperience = existing?.yearsExperience?.takeIf { it > 0 } ?: 9,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                // Demo-only shortcut, same escape hatch as
                // DoctorSessionViewModel.approveOwnClaimForDebug — applied here
                // instead of requiring a second debug tap after seeding, so the
                // dashboard is populated and visible the moment this returns.
                // Real verification stays a human, out-of-band review; this
                // method is unreachable outside a debug build (see class doc).
                verificationStatus = VerificationStatus.VERIFIED.name,
                verifiedAt = now,
                bio = existing?.bio.orDefault(
                    "Board-certified dermatologist focused on early skin cancer detection and follow-up care.",
                ),
            ),
        )

        val patients = DemoDataPlan.doctorPatients(now)
        var scanCount = 0
        patients.forEachIndexed { index, patient ->
            val patientUserId = "demo_patient_${suffix}_$index"
            val oldestScanDaysAgo = patient.scans.maxOf { it.daysAgo }
            // Linked a week before their earliest seeded scan, and consented
            // immediately — the demo doctor never needs to re-enact the invite
            // flow to have an already-active roster.
            val linkedAt = now - (oldestScanDaysAgo + 7L) * DAY_MS

            patientLinkDao.upsert(
                PatientLinkEntity(
                    id = "demo_link_${suffix}_$index",
                    userId = userId,
                    doctorId = doctorId,
                    patientUserId = patientUserId,
                    patientDisplayName = patient.displayName,
                    linkedAt = linkedAt,
                    createdAt = linkedAt,
                    updatedAt = now,
                    status = "ACTIVE",
                    consentGrantedAt = linkedAt,
                ),
            )

            patient.scans.forEachIndexed { scanIndex, scan ->
                writeScan(
                    scanId = "demo_scan_${suffix}_${index}_$scanIndex",
                    ownerUserId = patientUserId,
                    now = now,
                    scan = scan,
                )
                scanCount++
            }
        }

        return DemoSeedResult(mode = DemoSeedMode.DOCTOR, peopleSeeded = patients.size, scansSeeded = scanCount)
    }

    private suspend fun seedPatient(userId: String, now: Long): DemoSeedResult {
        val existing = userProfileDetailsDao.getById(userId)
        userProfileDetailsDao.upsert(
            UserProfileDetailsEntity(
                userId = userId,
                age = existing?.age?.takeIf { it > 0 } ?: 29,
                gender = existing?.gender.orDefault("Female"),
                skinType = existing?.skinType.orDefault("Combination"),
                skinTone = existing?.skinTone.orDefault("Medium"),
                skinConcerns = existing?.skinConcerns.orDefault("Occasional acne, sun spots"),
                allergies = existing?.allergies.orDefault("None known"),
                medications = existing?.medications.orDefault("None"),
                sunExposure = existing?.sunExposure.orDefault("15-30 min daily"),
                waterIntake = existing?.waterIntake.orDefault("2L / day"),
                sleepHours = existing?.sleepHours.orDefault("7"),
                stressLevel = existing?.stressLevel.orDefault("Moderate"),
                diet = existing?.diet.orDefault("Balanced, mostly home-cooked"),
                smoking = existing?.smoking ?: false,
                alcohol = existing?.alcohol ?: false,
                exercise = existing?.exercise.orDefault("3x / week"),
                skinCareRoutine = existing?.skinCareRoutine.orDefault("Cleanser, SPF 50, night moisturizer"),
                language = existing?.language.orDefault("en"),
                createdAt = existing?.createdAt ?: now,
            ),
        )

        val suffix = shortId(userId)
        val timeline = DemoDataPlan.patientTimeline(now)
        timeline.forEachIndexed { index, scan ->
            writeScan(
                scanId = "demo_scan_self_${suffix}_$index",
                ownerUserId = userId,
                now = now,
                scan = scan,
            )
        }

        return DemoSeedResult(mode = DemoSeedMode.PATIENT, peopleSeeded = 1, scansSeeded = timeline.size)
    }

    private suspend fun writeScan(scanId: String, ownerUserId: String, now: Long, scan: DemoScanSpec) {
        val capturedAt = now - scan.daysAgo.toLong() * DAY_MS
        val photoPath = renderScanPhoto(scanId, ownerUserId, scan)
        skinScanDao.upsert(
            SkinScanEntity(
                id = scanId,
                userId = ownerUserId,
                imagePath = photoPath,
                thumbnailPath = photoPath,
                capturedAt = capturedAt,
                note = scan.note,
                bodyArea = scan.bodyArea,
                createdAt = capturedAt,
                updatedAt = capturedAt,
            ),
        )
        scanPredictionDao.upsertAll(
            scan.predictions.mapIndexed { rankIndex, prediction ->
                ScanPredictionEntity(
                    id = "${scanId}_pred_$rankIndex",
                    scanId = scanId,
                    label = prediction.label,
                    labelCode = prediction.code,
                    confidence = prediction.confidence,
                    rank = rankIndex + 1,
                    concernBand = prediction.severity.name,
                    createdAt = capturedAt,
                )
            },
        )
    }

    /**
     * Renders this scan's synthetic photo to `filesDir/photos/` and returns its
     * absolute path, or [PLACEHOLDER_IMAGE_PATH] (a path that does not exist,
     * which every display path already falls back safely on) if rendering
     * throws for any reason — a demo tool must never crash the seed action
     * over a cosmetic failure.
     */
    private fun renderScanPhoto(scanId: String, ownerUserId: String, scan: DemoScanSpec): String =
        runCatching {
            val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(photosDir, "demo_$scanId.jpg")
            val topSeverity = scan.predictions.first().severity
            DemoPhotoGenerator.render(
                outFile = file,
                ownerSeed = ownerUserId,
                scanId = scanId,
                topSeverity = topSeverity,
            )
            file.absolutePath
        }.getOrDefault(PLACEHOLDER_IMAGE_PATH)

    /** First non-blank wins; `?.` chain reads awkwardly repeated inline four times over. */
    private fun String?.orDefault(fallback: String): String = this?.takeIf { it.isNotBlank() } ?: fallback

    /**
     * Short, deterministic per-account suffix so demo ids never collide if two
     * different accounts are ever seeded on the same device (e.g. testing on
     * one phone before the demo). Not a security boundary — just id hygiene.
     */
    private fun shortId(userId: String): String =
        userId.filter { it.isLetterOrDigit() }.take(8).lowercase(Locale.ROOT).ifEmpty { "acct" }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000

        /**
         * Deliberately a path that does not exist. See the class doc's "what is
         * real vs. fake" section for why a missing file here is safe rather than
         * a crash risk.
         */
        const val PLACEHOLDER_IMAGE_PATH = "demo_seed/no_photo_available.jpg"
    }
}
