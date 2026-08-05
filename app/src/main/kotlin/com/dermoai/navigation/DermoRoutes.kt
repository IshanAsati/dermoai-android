package com.dermoai.navigation

import kotlinx.serialization.Serializable

// ── Auth routes (no bottom bar) ───────────────────────────────────────────

@Serializable
object SplashRoute

@Serializable
object OnboardingRoute

@Serializable
object SignInRoute

@Serializable
object SignUpRoute

/** Registration for healthcare professionals — collects credentials for manual review. */
@Serializable
object DoctorSignUpRoute

/**
 * Holding screen for a doctor whose credentials have not been verified yet.
 * Verification is a manual, out-of-band process, so this is a genuine terminal
 * state rather than a step the user can complete in-app.
 */
@Serializable
object DoctorStatusRoute

// ── Tab destinations (bottom bar visible) ─────────────────────────────────

/** Marker interface for top-level tab routes — used to toggle bottom bar. */
sealed interface TabRoute

@Serializable
object HomeTab : TabRoute

@Serializable
object TimelineTab : TabRoute

@Serializable
object ScanTab : TabRoute

@Serializable
object SkinMindTab : TabRoute

@Serializable
object MoreTab : TabRoute

// ── Scan sub-routes (bottom bar hidden — immersive camera) ────────────────

@Serializable
object ScanCaptureRoute

@Serializable
data class ScanReviewRoute(val photoPath: String)

@Serializable
data class ScanResultsRoute(val photoPath: String)

// ── More hub sub-routes (bottom bar hidden — pushed stack) ────────────────

@Serializable
object TreatmentRoute

@Serializable
object WellnessRoute

@Serializable
object AnalyticsRoute

@Serializable
object ReportsRoute

@Serializable
object SettingsRoute
@Serializable
object FaqRoute
@Serializable
object FindDermatologistRoute
@Serializable
object BreathingRoute
@Serializable
object JournalRoute

// ── Timeline sub-routes ───────────────────────────────────────────────────

@Serializable
data class TimelineDetailRoute(val scanId: String)

// ── Doctor dashboard ──────────────────────────────────────────────────────
//
// The dashboard is a destination in its own right rather than a tab: a verified
// doctor lands here instead of the patient home, and the patient bottom bar
// (Scan / Timeline / SkinMind) is meaningless in that context.

@Serializable
object DoctorDashboardRoute

@Serializable
data class PatientDetailRoute(
    val patientUserId: String,
    val patientDisplayName: String,
)

@Serializable
object InvitePatientRoute

// ── Patient-side counterparts to the doctor flow ──────────────────────────

/** Where a patient redeems an invite code and gives explicit consent. */
@Serializable
object RedeemInviteRoute

/** Which doctors have looked at this patient's data, and revocation. */
@Serializable
object PatientPrivacyRoute
