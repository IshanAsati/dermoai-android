/**
 * Input validation for forms. Pure and framework-free so it is unit-tested.
 * Limits match the Appwrite attribute sizes in `setup_collections.py`; a value
 * over the limit is rejected here with a readable message rather than by
 * Appwrite with an opaque 400.
 */
export const LIMITS = {
  name: 200,
  email: 254,
  registrationNumber: 100,
  specialty: 150,
  institution: 200,
  qualificationsTotal: 2000,
  bio: 2000,
  passwordMin: 8,
  passwordMax: 256,
  yearsMax: 70,
} as const;

export type FieldErrors = Record<string, string>;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export function clean(value: FormDataEntryValue | null | undefined): string {
  return typeof value === "string" ? value.trim() : "";
}

export function validateEmail(email: string): string | null {
  if (!email) return "Enter your email address.";
  if (email.length > LIMITS.email || !EMAIL.test(email)) return "Enter a valid email address.";
  return null;
}

export function validatePassword(password: string): string | null {
  if (password.length < LIMITS.passwordMin) return `Use at least ${LIMITS.passwordMin} characters.`;
  if (password.length > LIMITS.passwordMax) return "That password is too long.";
  return null;
}

export type ProfileFields = {
  fullName: string;
  registrationNumber: string;
  specialty: string;
  institution: string;
  yearsExperience: number;
  qualifications: string[];
  bio: string;
};

/** Qualifications may be separated by commas or new lines. */
export function parseQualifications(raw: string): string[] {
  return raw
    .split(/[\n,]/)
    .map((q) => q.trim())
    .filter(Boolean)
    .filter((q, i, all) => all.indexOf(q) === i);
}

export function validateProfile(form: FormData): { value?: ProfileFields; errors: FieldErrors } {
  const errors: FieldErrors = {};
  const fullName = clean(form.get("fullName"));
  const registrationNumber = clean(form.get("registrationNumber"));
  const specialty = clean(form.get("specialty"));
  const institution = clean(form.get("institution"));
  const bio = clean(form.get("bio"));
  const yearsRaw = clean(form.get("yearsExperience"));
  const qualifications = parseQualifications(clean(form.get("qualifications")));

  if (!fullName) errors.fullName = "Enter your full name.";
  else if (fullName.length > LIMITS.name) errors.fullName = `Keep this under ${LIMITS.name} characters.`;

  if (!registrationNumber) errors.registrationNumber = "Enter your medical registration number.";
  else if (registrationNumber.length > LIMITS.registrationNumber) errors.registrationNumber = "That number is too long.";

  if (!specialty) errors.specialty = "Enter your specialty.";
  else if (specialty.length > LIMITS.specialty) errors.specialty = `Keep this under ${LIMITS.specialty} characters.`;

  if (!institution) errors.institution = "Enter your clinic or hospital.";
  else if (institution.length > LIMITS.institution) errors.institution = `Keep this under ${LIMITS.institution} characters.`;

  let yearsExperience = 0;
  if (yearsRaw !== "") {
    const years = Number(yearsRaw);
    if (!Number.isInteger(years) || years < 0 || years > LIMITS.yearsMax) {
      errors.yearsExperience = `Enter a whole number from 0 to ${LIMITS.yearsMax}.`;
    } else {
      yearsExperience = years;
    }
  }

  if (qualifications.join("\n").length > LIMITS.qualificationsTotal) errors.qualifications = "That list is too long.";
  if (bio.length > LIMITS.bio) errors.bio = `Keep this under ${LIMITS.bio} characters.`;

  if (Object.keys(errors).length > 0) return { errors };
  return { errors, value: { fullName, registrationNumber, specialty, institution, yearsExperience, qualifications, bio } };
}
