package com.dermoai.feature.settings.demo

import com.dermoai.core.domain.model.ConditionSeverity

/**
 * One ranked model finding attached to a synthetic scan.
 *
 * Label/code/severity triples are taken straight from the real taxonomy the
 * on-device model ships with (`app/src/main/assets/ml/labels.txt` and
 * `TfliteInterpreterHolder.LABEL_CODES`/`severityFor`), so a seeded scan looks
 * exactly like one the actual inference pipeline would have written — the
 * doctor dashboard and timeline never have to know the row was seeded rather
 * than scored.
 */
data class DemoPredictionSpec(
    val label: String,
    val code: String,
    val severity: ConditionSeverity,
    val confidence: Float,
)

/**
 * One synthetic scan: how long before "now" it was captured, where on the
 * body, and what the model would have said, ranked highest-confidence first.
 */
data class DemoScanSpec(
    val daysAgo: Int,
    val bodyArea: String,
    val note: String,
    val predictions: List<DemoPredictionSpec>,
)

/** One synthetic patient linked to the demo doctor account. */
data class DemoPatientSpec(
    val displayName: String,
    val scans: List<DemoScanSpec>,
)

/**
 * Fixed, deterministic demo content for [com.dermoai.feature.settings.demo.DemoDataSeeder].
 *
 * Deliberately hardcoded rather than randomised: a demo tapped twice by a
 * nervous presenter the night before a competition must produce the exact
 * same story both times, and a reviewer reading this file should be able to
 * see the whole narrative — who is urgent, who is stable, who has gone quiet
 * — without running anything.
 *
 * All fields here are pure data with no dependency on Room, Hilt or Android,
 * so the shape (counts, severity spread, day ranges) is unit-testable on the
 * JVM without a database.
 */
object DemoDataPlan {

    /**
     * Four patients for the doctor's triage inbox, each telling a different
     * clinical story so [com.dermoai.feature.doctor.triage.TriageRanking] visibly
     * sorts them instead of rendering four identical rows:
     *
     *  1. **Ishaan Kapoor** — severity climbing scan over scan, ending CRITICAL.
     *     Ranks first: the algorithm's top tier is "newest scan's severity."
     *  2. **Kavya Reddy** — only two old scans, nothing in the last two weeks.
     *     Low severity but INACTIVE adherence — the "go chase them" case.
     *  3. **Arjun Nair** — stable benign mole, but scanning less often than the
     *     weekly cadence expects. SLIPPING adherence.
     *  4. **Meera Iyer** — started with a flagged lesion, now trending down to
     *     healthy skin with regular scans. IMPROVING and GOOD adherence — the
     *     well-managed, lowest-urgency patient, and so ranks last.
     */
    fun doctorPatients(now: Long): List<DemoPatientSpec> = listOf(
        DemoPatientSpec(
            displayName = "Ishaan Kapoor",
            scans = listOf(
                DemoScanSpec(
                    daysAgo = 21,
                    bodyArea = "Right shoulder",
                    note = "New pigmented spot noticed after a beach trip",
                    predictions = listOf(
                        DemoPredictionSpec("Actinic Keratosis (ACK)", "ACK", ConditionSeverity.MEDIUM, 0.62f),
                        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 0.21f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 14,
                    bodyArea = "Right shoulder",
                    note = "Spot looks slightly larger, no pain",
                    predictions = listOf(
                        DemoPredictionSpec("Actinic Keratosis (ACK)", "ACK", ConditionSeverity.MEDIUM, 0.71f),
                        DemoPredictionSpec("Seborrheic Keratosis (SEK)", "SEK", ConditionSeverity.MEDIUM, 0.15f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 6,
                    bodyArea = "Right shoulder",
                    note = "Follow-up photo as requested at last visit",
                    predictions = listOf(
                        DemoPredictionSpec("Basal Cell Carcinoma (BCC)", "BCC", ConditionSeverity.HIGH, 0.68f),
                        DemoPredictionSpec("Actinic Keratosis (ACK)", "ACK", ConditionSeverity.MEDIUM, 0.19f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 2,
                    bodyArea = "Right shoulder",
                    note = "Border looks more irregular this week",
                    predictions = listOf(
                        DemoPredictionSpec("Melanoma (MEL)", "MEL", ConditionSeverity.CRITICAL, 0.74f),
                        DemoPredictionSpec("Basal Cell Carcinoma (BCC)", "BCC", ConditionSeverity.HIGH, 0.16f),
                    ),
                ),
            ),
        ),
        DemoPatientSpec(
            displayName = "Kavya Reddy",
            scans = listOf(
                DemoScanSpec(
                    daysAgo = 51,
                    bodyArea = "Chin",
                    note = "Mild breakout, no treatment yet",
                    predictions = listOf(
                        DemoPredictionSpec("Acne and Rosacea", "Acne", ConditionSeverity.LOW, 0.66f),
                        DemoPredictionSpec("Seborrheic Keratosis (SEK)", "SEK", ConditionSeverity.MEDIUM, 0.14f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 45,
                    bodyArea = "Chin",
                    note = "Slight improvement noticed",
                    predictions = listOf(
                        DemoPredictionSpec("Acne and Rosacea", "Acne", ConditionSeverity.LOW, 0.60f),
                        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, 0.18f),
                    ),
                ),
            ),
        ),
        DemoPatientSpec(
            displayName = "Arjun Nair",
            scans = listOf(
                DemoScanSpec(
                    daysAgo = 56,
                    bodyArea = "Left forearm",
                    note = "Small mole present for years, no changes",
                    predictions = listOf(
                        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 0.80f),
                        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, 0.10f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 28,
                    bodyArea = "Left forearm",
                    note = "Routine monthly check",
                    predictions = listOf(
                        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 0.77f),
                        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, 0.12f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 10,
                    bodyArea = "Left forearm",
                    note = "Still unchanged, doctor advised routine monitoring",
                    predictions = listOf(
                        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 0.79f),
                        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, 0.11f),
                    ),
                ),
            ),
        ),
        DemoPatientSpec(
            displayName = "Meera Iyer",
            scans = listOf(
                DemoScanSpec(
                    daysAgo = 40,
                    bodyArea = "Upper back",
                    note = "Doctor flagged this mole during routine checkup",
                    predictions = listOf(
                        DemoPredictionSpec("Basal Cell Carcinoma (BCC)", "BCC", ConditionSeverity.HIGH, 0.58f),
                        DemoPredictionSpec("Seborrheic Keratosis (SEK)", "SEK", ConditionSeverity.MEDIUM, 0.20f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 30,
                    bodyArea = "Upper back",
                    note = "Started using recommended SPF daily",
                    predictions = listOf(
                        DemoPredictionSpec("Actinic Keratosis (ACK)", "ACK", ConditionSeverity.MEDIUM, 0.55f),
                        DemoPredictionSpec("Seborrheic Keratosis (SEK)", "SEK", ConditionSeverity.MEDIUM, 0.22f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 18,
                    bodyArea = "Upper back",
                    note = "Lesion looks calmer than last visit",
                    predictions = listOf(
                        DemoPredictionSpec("Seborrheic Keratosis (SEK)", "SEK", ConditionSeverity.MEDIUM, 0.60f),
                        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 0.18f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 9,
                    bodyArea = "Upper back",
                    note = "Continued improvement, doctor pleased",
                    predictions = listOf(
                        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 0.66f),
                        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, 0.20f),
                    ),
                ),
                DemoScanSpec(
                    daysAgo = 3,
                    bodyArea = "Upper back",
                    note = "Looks stable and healed well",
                    predictions = listOf(
                        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, 0.72f),
                        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 0.15f),
                    ),
                ),
            ),
        ),
    )

    /**
     * ~20 scans for the demo patient's own timeline, spread across roughly the
     * last three months so the timeline and insights screens read as a
     * long-time, consistent user rather than someone who just installed the
     * app. Mostly benign, with two findings — an ACK at 35 days and a BCC at
     * 10 days — written up as flagged-for-follow-up, matching the "mostly
     * benign with one or two flagged" brief.
     *
     * [now] is unused today but kept as a parameter (mirroring
     * [doctorPatients]) so a future version can vary content by day-of-week or
     * season without changing every call site.
     */
    @Suppress("UNUSED_PARAMETER")
    fun patientTimeline(now: Long): List<DemoScanSpec> = listOf(
        DemoScanSpec(88, "Left cheek", "First scan after hearing about the app", nev(0.74f)),
        DemoScanSpec(81, "Right forearm", "Small freckle, watching for changes", nev(0.70f)),
        DemoScanSpec(74, "Upper back", "Routine weekly check", healthy(0.81f)),
        DemoScanSpec(68, "Neck", "A bit of redness after a long run", acne(0.55f)),
        DemoScanSpec(61, "Left shin", "Old scar, checking it hasn't changed", nev(0.68f)),
        DemoScanSpec(55, "Scalp", "Routine weekly check", healthy(0.77f)),
        DemoScanSpec(49, "Right shoulder", "Sunburn peeling, keeping an eye on it", healthy(0.63f)),
        DemoScanSpec(44, "Chest", "Small bump, no pain", sek(0.52f)),
        DemoScanSpec(39, "Left hand", "Routine weekly check", nev(0.71f)),
        DemoScanSpec(
            daysAgo = 35,
            bodyArea = "Right cheek",
            note = "New spot the derm wants monitored — flagged for follow-up",
            predictions = listOf(
                DemoPredictionSpec("Actinic Keratosis (ACK)", "ACK", ConditionSeverity.MEDIUM, 0.61f),
                DemoPredictionSpec("Seborrheic Keratosis (SEK)", "SEK", ConditionSeverity.MEDIUM, 0.17f),
            ),
        ),
        DemoScanSpec(30, "Lower back", "Routine weekly check", healthy(0.80f)),
        DemoScanSpec(25, "Right cheek", "Follow-up photo on the flagged spot", ackFollowUp(0.58f)),
        DemoScanSpec(21, "Left cheek", "Routine weekly check", nev(0.72f)),
        DemoScanSpec(17, "Right forearm", "Mild dryness, added moisturizer", healthy(0.66f)),
        DemoScanSpec(
            daysAgo = 10,
            bodyArea = "Right cheek",
            note = "Spot from last month looks different — flagged for dermatologist follow-up, appointment scheduled",
            predictions = listOf(
                DemoPredictionSpec("Basal Cell Carcinoma (BCC)", "BCC", ConditionSeverity.HIGH, 0.64f),
                DemoPredictionSpec("Actinic Keratosis (ACK)", "ACK", ConditionSeverity.MEDIUM, 0.20f),
            ),
        ),
        DemoScanSpec(7, "Upper back", "Routine weekly check", healthy(0.83f)),
        DemoScanSpec(5, "Right cheek", "Checking the flagged spot again before the appointment", ackFollowUp(0.49f)),
        DemoScanSpec(3, "Left shin", "Routine check", nev(0.69f)),
        DemoScanSpec(1, "Scalp", "Routine check, nothing new", healthy(0.85f)),
    )

    private fun nev(confidence: Float) = listOf(
        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, confidence),
        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, 1f - confidence - 0.1f),
    )

    private fun healthy(confidence: Float) = listOf(
        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, confidence),
        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 1f - confidence - 0.1f),
    )

    private fun acne(confidence: Float) = listOf(
        DemoPredictionSpec("Acne and Rosacea", "Acne", ConditionSeverity.LOW, confidence),
        DemoPredictionSpec("Healthy / Normal Skin", "Healthy", ConditionSeverity.LOW, 1f - confidence - 0.1f),
    )

    private fun sek(confidence: Float) = listOf(
        DemoPredictionSpec("Seborrheic Keratosis (SEK)", "SEK", ConditionSeverity.MEDIUM, confidence),
        DemoPredictionSpec("Nevus / Mole (NEV)", "NEV", ConditionSeverity.LOW, 1f - confidence - 0.1f),
    )

    /** Same finding as the flagged scan, slightly lower confidence — a realistic re-photograph. */
    private fun ackFollowUp(confidence: Float) = listOf(
        DemoPredictionSpec("Actinic Keratosis (ACK)", "ACK", ConditionSeverity.MEDIUM, confidence),
        DemoPredictionSpec("Seborrheic Keratosis (SEK)", "SEK", ConditionSeverity.MEDIUM, 1f - confidence - 0.15f),
    )
}
