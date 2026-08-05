package com.dermoai.core.data.sync

/**
 * The names of everything this app expects to find in Appwrite.
 *
 * Kept in one object because there are two independent implementations of this
 * schema — this module, and `tools/appwrite/setup_collections.py`, which
 * actually creates it. Two implementations that disagree about a field name
 * fail at runtime with a 400 that names an attribute nobody can grep for, so
 * both sides quote *these* strings and the Python script carries a comment
 * pointing back here. If you rename anything, rename it in both places and
 * re-run the setup script.
 *
 * ## What is deliberately NOT here
 * There is no collection for scan images, thumbnails, or the TFLite model.
 * That is not an oversight and not a "later" — it is the design:
 *
 *  - The model is ~90 MB of weights that every install already has bundled;
 *    uploading it per user would be pure waste.
 *  - Scan photographs are the most sensitive thing this app touches. Keeping
 *    them on the device that took them means a backend compromise leaks
 *    metadata and triage summaries, not a patient's medical photographs.
 *  - Cross-device *linking* — the actual feature — does not need pixels. A
 *    doctor triaging a list needs "when, where on the body, what the model
 *    said, how confident, how concerning". That is [SCAN_SUMMARIES], and it is
 *    a derived summary precisely so no image or file path ever leaves the phone.
 *
 * A doctor who needs to see the photograph itself asks the patient to show it,
 * or the product grows an explicit, separately-consented image-sharing feature.
 * It should not arrive by accident through a sync layer.
 */
object AppwriteSchema {

    /** Clinician credentials. One document per doctor account, id = local row id. */
    const val DOCTOR_PROFILES = "doctor_profiles"

    /** Doctor↔patient consent records. The authorisation boundary of the feature. */
    const val PATIENT_LINKS = "patient_links"

    /** Short redeemable codes that create a [PATIENT_LINKS] row. */
    const val DOCTOR_INVITES = "doctor_invites"

    /** Derived, image-free triage rows. See the note above on why they are derived. */
    const val SCAN_SUMMARIES = "scan_summaries"

    /** Append-only record of a doctor touching a patient's data. */
    const val AUDIT_ENTRIES = "audit_entries"

    /** Attribute keys, grouped by collection. */
    object Fields {
        object DoctorProfiles {
            const val USER_ID = "userId"
            const val FULL_NAME = "fullName"

            /**
             * Newline-joined, exactly as `DoctorProfileEntity` stores it. Encoded
             * as one string rather than an Appwrite array attribute so the round
             * trip through Room is lossless and no separator logic exists twice.
             */
            const val QUALIFICATIONS = "qualifications"
            const val REGISTRATION_NUMBER = "registrationNumber"
            const val SPECIALTY = "specialty"
            const val INSTITUTION = "institution"
            const val YEARS_EXPERIENCE = "yearsExperience"
            const val VERIFICATION_STATUS = "verificationStatus"
            const val VERIFIED_AT = "verifiedAt"
            const val BIO = "bio"
        }

        object PatientLinks {
            const val DOCTOR_ID = "doctorId"
            const val PATIENT_USER_ID = "patientUserId"
            const val PATIENT_DISPLAY_NAME = "patientDisplayName"
            const val LINKED_AT = "linkedAt"
            const val STATUS = "status"
            const val CONSENT_GRANTED_AT = "consentGrantedAt"

            /**
             * The doctor's *account* id, distinct from [DOCTOR_ID] which is a
             * profile row id. Carried because document permissions are written
             * in terms of accounts, and a link whose ACL cannot be derived from
             * its own fields cannot be re-granted after a pull.
             */
            const val DOCTOR_USER_ID = "doctorUserId"
        }

        object DoctorInvites {
            const val DOCTOR_ID = "doctorId"
            const val DOCTOR_USER_ID = "doctorUserId"
            const val CODE = "code"
            const val CREATED_AT = "createdAt"
            const val EXPIRES_AT = "expiresAt"
            const val MAX_USES = "maxUses"
            const val USED_COUNT = "usedCount"
            const val REVOKED = "revoked"
        }

        object ScanSummaries {
            const val PATIENT_USER_ID = "patientUserId"
            const val SCAN_ID = "scanId"
            const val CAPTURED_AT = "capturedAt"
            const val TOP_LABEL = "topLabel"
            const val TOP_LABEL_CODE = "topLabelCode"
            const val CONFIDENCE = "confidence"
            const val CONCERN_BAND = "concernBand"
            const val BODY_AREA = "bodyArea"
        }

        object AuditEntries {
            const val ACTOR_USER_ID = "actorUserId"
            const val SUBJECT_USER_ID = "subjectUserId"
            const val ACTION = "action"
            const val AT = "at"
            const val DETAIL = "detail"
        }
    }

    /**
     * Appwrite caps a page at 100 documents. Stated here rather than inlined so
     * the pull methods and any future pagination agree on the number.
     */
    const val MAX_PAGE_SIZE = 100
}
