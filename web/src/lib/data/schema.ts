/**
 * Wire shapes for the five Appwrite collections, mirroring `AppwriteSchema.kt`
 * and `tools/appwrite/setup_collections.py`. Field names here are the contract
 * with the Android app: rename nothing without changing both.
 *
 * Every read goes through a coercing accessor and never throws. JSON has one
 * number type and a half-synced row is normal, so a partly unreadable document
 * costs the fields it broke, not the whole dashboard. Security-relevant fields
 * fail closed: an unreadable status is not ACTIVE, an unreadable expiry is
 * already expired, an unreadable `revoked` is revoked.
 */
export const Collections = {
  doctorProfiles: "doctor_profiles",
  patientLinks: "patient_links",
  doctorInvites: "doctor_invites",
  scanSummaries: "scan_summaries",
  auditEntries: "audit_entries",
} as const;

/** The subset of an Appwrite document the data layer relies on. */
export type RawDoc = {
  $id: string;
  $permissions?: string[];
  [key: string]: unknown;
};

export const str = (v: unknown, fallback = ""): string => (typeof v === "string" ? v : fallback);

export const num = (v: unknown, fallback = 0): number => {
  const n = typeof v === "number" ? v : typeof v === "string" && v.trim() !== "" ? Number(v) : NaN;
  return Number.isFinite(n) ? n : fallback;
};

export const numOrNull = (v: unknown): number | null => {
  const n = typeof v === "number" ? v : typeof v === "string" && v.trim() !== "" ? Number(v) : NaN;
  return Number.isFinite(n) ? n : null;
};

export const bool = (v: unknown, fallback: boolean): boolean => (typeof v === "boolean" ? v : fallback);

/** Appwrite's $createdAt / $updatedAt are ISO strings on the server's clock. 0 when absent. */
const parseTime = (v: unknown): number => {
  const t = typeof v === "string" ? Date.parse(v) : NaN;
  return Number.isFinite(t) ? t : 0;
};

// ── doctor_profiles ─────────────────────────────────────────────────────────

export type VerificationStatus = "UNVERIFIED" | "PENDING" | "VERIFIED" | "REJECTED";

export type DoctorProfile = {
  id: string;
  userId: string;
  fullName: string;
  qualifications: string[];
  registrationNumber: string;
  specialty: string;
  institution: string;
  yearsExperience: number;
  bio: string;
};

export function profileFromDoc(doc: RawDoc): DoctorProfile {
  return {
    id: doc.$id,
    userId: str(doc.userId),
    fullName: str(doc.fullName),
    qualifications: str(doc.qualifications)
      .split("\n")
      .map((q) => q.trim())
      .filter(Boolean),
    registrationNumber: str(doc.registrationNumber),
    specialty: str(doc.specialty),
    institution: str(doc.institution),
    yearsExperience: Math.max(0, Math.trunc(num(doc.yearsExperience))),
    bio: str(doc.bio),
  };
}

export type ProfileInput = Omit<DoctorProfile, "id" | "userId">;

/**
 * `verificationStatus` is written once, as UNVERIFIED, on create and never by an
 * edit. A doctor can edit their own document directly through Appwrite, so that
 * field is display data only: the authority on verification is the
 * `verifieddoctor` account label, which only the API key can set. See
 * `lib/doctor-context.ts`.
 */
export function profileToData(userId: string, input: ProfileInput): Record<string, unknown> {
  return {
    userId,
    ...profileFields(input),
    verificationStatus: "UNVERIFIED",
  };
}

/** Everything an edit may change. Deliberately excludes userId and verificationStatus. */
export function profileFields(input: ProfileInput): Record<string, unknown> {
  return {
    fullName: input.fullName,
    qualifications: input.qualifications.join("\n"),
    registrationNumber: input.registrationNumber,
    specialty: input.specialty,
    institution: input.institution,
    yearsExperience: input.yearsExperience,
    bio: input.bio,
  };
}

// ── doctor_invites ──────────────────────────────────────────────────────────

export type Invite = {
  id: string;
  doctorId: string;
  doctorUserId: string;
  code: string;
  /** Doctor-side clock when the code was made. Display and expiry only. */
  createdAt: number;
  /**
   * When Appwrite first stored the invite, by the server's clock. Pairing is
   * attributed against this and a link's own `storedAt`: both come from the
   * same clock, so they compare exactly and need no tolerance.
   */
  storedAt: number;
  expiresAt: number;
  maxUses: number;
  usedCount: number;
  revoked: boolean;
};

export function inviteFromDoc(doc: RawDoc): Invite {
  return {
    id: doc.$id,
    doctorId: str(doc.doctorId),
    doctorUserId: str(doc.doctorUserId),
    code: str(doc.code).toUpperCase(),
    createdAt: num(doc.createdAt),
    storedAt: parseTime(doc.$createdAt) || num(doc.createdAt),
    // Absent expiry reads as already expired, absent `revoked` as revoked.
    expiresAt: num(doc.expiresAt, 0),
    maxUses: Math.max(1, Math.trunc(num(doc.maxUses, 1))),
    usedCount: Math.max(0, Math.trunc(num(doc.usedCount))),
    revoked: bool(doc.revoked, true),
  };
}

export type InviteState = "ACTIVE" | "REVOKED" | "EXPIRED" | "USED";

export function inviteState(invite: Invite, now: number): InviteState {
  // Spent wins over revoked: the app auto-revokes a code the moment its last use is
  // taken, and "Cancelled" would misreport a pairing that actually happened.
  if (invite.usedCount >= invite.maxUses) return "USED";
  if (invite.revoked) return "REVOKED";
  if (now >= invite.expiresAt) return "EXPIRED";
  return "ACTIVE";
}

// ── patient_links ───────────────────────────────────────────────────────────

export type LinkStatus = "INVITED" | "ACTIVE" | "REVOKED";

export type PatientLink = {
  id: string;
  doctorId: string;
  doctorUserId: string;
  patientUserId: string;
  patientDisplayName: string;
  /** Patient device clock. Display only: it can be wrong or forged. */
  linkedAt: number;
  /**
   * When Appwrite first stored the document, by the server's clock. Used to
   * decide whether the link plausibly came from one of this doctor's invites.
   */
  storedAt: number;
  /** When Appwrite last stored a change to the document, by the server's clock. */
  updatedAt: number;
  status: LinkStatus;
  consentGrantedAt: number | null;
  /**
   * The Appwrite account that holds update rights on this document, i.e. the
   * session that wrote it. A genuine link is written by the patient's device,
   * so this is the patient's session, never the doctor's. Null if none found.
   */
  authorUserId: string | null;
};

const USER_UPDATE = /^update\("user:([^"/]+)(?:\/[a-z]+)?"\)$/;
const USER_READ = /^read\("user:([^"/]+)(?:\/[a-z]+)?"\)$/;

export function authorOf(permissions: readonly string[] | undefined): string | null {
  for (const p of permissions ?? []) {
    const m = USER_UPDATE.exec(p.trim());
    if (m) return m[1];
  }
  return null;
}

export function readersOf(permissions: readonly string[] | undefined): string[] {
  const out: string[] = [];
  for (const p of permissions ?? []) {
    const m = USER_READ.exec(p.trim());
    if (m) out.push(m[1]);
  }
  return out;
}

export function linkFromDoc(doc: RawDoc): PatientLink {
  const status = str(doc.status);
  return {
    id: doc.$id,
    doctorId: str(doc.doctorId),
    doctorUserId: str(doc.doctorUserId),
    patientUserId: str(doc.patientUserId),
    patientDisplayName: str(doc.patientDisplayName),
    linkedAt: num(doc.linkedAt),
    storedAt: parseTime(doc.$createdAt),
    updatedAt: parseTime(doc.$updatedAt),
    // Unknown or absent is INVITED, which grants nothing.
    status: status === "ACTIVE" || status === "REVOKED" ? status : "INVITED",
    consentGrantedAt: numOrNull(doc.consentGrantedAt),
    authorUserId: authorOf(doc.$permissions),
  };
}

// ── scan_summaries ──────────────────────────────────────────────────────────

export type ScanSummary = {
  id: string;
  patientUserId: string;
  scanId: string;
  capturedAt: number;
  topLabel: string;
  topLabelCode: string;
  confidence: number;
  concernBand: string;
  bodyArea: string;
  /** Appwrite accounts with read access: the owner, never a doctor. */
  readers: string[];
};

export function summaryFromDoc(doc: RawDoc): ScanSummary {
  return {
    id: doc.$id,
    patientUserId: str(doc.patientUserId),
    scanId: str(doc.scanId),
    capturedAt: num(doc.capturedAt),
    topLabel: str(doc.topLabel),
    topLabelCode: str(doc.topLabelCode),
    confidence: Math.min(1, Math.max(0, num(doc.confidence))),
    concernBand: str(doc.concernBand),
    bodyArea: str(doc.bodyArea),
    readers: readersOf(doc.$permissions),
  };
}

// ── audit_entries ───────────────────────────────────────────────────────────

export type AuditAction = "VIEWED_PATIENT" | "VIEWED_SCAN" | "EXPORTED_REPORT" | "LINKED_PATIENT" | "REVOKED_LINK";

export type AuditEntry = {
  id: string;
  actorUserId: string;
  subjectUserId: string;
  action: string;
  at: number;
  /** Appwrite's server clock at first write. Used to order against link updates. */
  storedAt: number;
  detail: string;
};

export function auditFromDoc(doc: RawDoc): AuditEntry {
  return {
    id: doc.$id,
    actorUserId: str(doc.actorUserId),
    subjectUserId: str(doc.subjectUserId),
    action: str(doc.action),
    at: num(doc.at),
    storedAt: parseTime(doc.$createdAt),
    detail: str(doc.detail),
  };
}
