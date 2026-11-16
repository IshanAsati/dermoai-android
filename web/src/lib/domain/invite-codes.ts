/**
 * Invite codes. Must stay byte-compatible with the Android app
 * (`DoctorInvite.CODE_ALPHABET`, `CODE_LENGTH`, `InviteCodes.DEEP_LINK_PREFIX`):
 * the patient's field and QR scanner accept exactly this shape and nothing else.
 */
export const CODE_LENGTH = 8;

/** Omits 0/O and 1/I/L, the characters people mistype when a code is read aloud. */
export const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

/**
 * The Android in-app scanner ignores any QR whose payload does not start with
 * this, so the QR must encode the deep link and not a web URL.
 */
export const DEEP_LINK_PREFIX = "dermoai://invite/";

export type RandomInt = (maxExclusive: number) => number;

/**
 * @param randomInt must be a cryptographically strong source (`crypto.randomInt`).
 *   A guessable generator turns an 8-character credential into no credential.
 */
export function generateCode(randomInt: RandomInt): string {
  let code = "";
  for (let i = 0; i < CODE_LENGTH; i += 1) {
    code += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  }
  return code;
}

const PASTED_PREFIX = /^(?:dermoai:\/\/invite\/|https?:\/\/[^/\s]+\/pair\/)/i;

/**
 * What a code field should hold for whatever was typed or pasted.
 *
 * Strips a pasted deep link or share link first. Without that, the letters of
 * "dermoai://invite/" survive the alphabet filter and the real code is
 * truncated away, producing a plausible-looking code that never matches.
 */
export function normaliseCode(raw: string): string {
  const withoutPrefix = raw.trim().replace(PASTED_PREFIX, "");
  return withoutPrefix
    .toUpperCase()
    .split("")
    .filter((ch) => CODE_ALPHABET.includes(ch))
    .join("")
    .slice(0, CODE_LENGTH);
}

export function isCompleteCode(code: string): boolean {
  return code.length === CODE_LENGTH && [...code].every((ch) => CODE_ALPHABET.includes(ch));
}

/** The exact string the QR encodes. */
export function deepLink(code: string): string {
  return DEEP_LINK_PREFIX + code;
}

/** `ABCD-2345`: display only. Never store or look up this form. */
export function formatCodeForDisplay(code: string): string {
  return code.length === CODE_LENGTH ? `${code.slice(0, 4)}-${code.slice(4)}` : code;
}
