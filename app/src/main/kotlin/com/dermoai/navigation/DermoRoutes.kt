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
object BreathingRoute
@Serializable
object JournalRoute

// ── Timeline sub-routes ───────────────────────────────────────────────────

@Serializable
data class TimelineDetailRoute(val scanId: String)
