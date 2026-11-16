import { randomInt } from "node:crypto";
import { describe, expect, it } from "vitest";
import {
  CODE_ALPHABET,
  CODE_LENGTH,
  deepLink,
  DEEP_LINK_PREFIX,
  formatCodeForDisplay,
  generateCode,
  isCompleteCode,
  normaliseCode,
} from "@/lib/domain/invite-codes";

describe("invite codes (must match the Android app)", () => {
  it("alphabet and length are exactly the Android constants", () => {
    expect(CODE_ALPHABET).toBe("ABCDEFGHJKMNPQRSTUVWXYZ23456789");
    expect(CODE_LENGTH).toBe(8);
    expect(DEEP_LINK_PREFIX).toBe("dermoai://invite/");
    for (const ambiguous of "0O1IL") expect(CODE_ALPHABET).not.toContain(ambiguous);
  });

  it("generates 8 chars drawn only from the alphabet", () => {
    for (let i = 0; i < 500; i += 1) {
      const code = generateCode(randomInt);
      expect(code).toHaveLength(8);
      expect(isCompleteCode(code)).toBe(true);
    }
  });

  it("uses every alphabet symbol roughly uniformly (no modulo bias)", () => {
    const counts = new Map<string, number>();
    const total = 31 * 2000;
    for (let i = 0; i < total / 8; i += 1) {
      for (const ch of generateCode(randomInt)) counts.set(ch, (counts.get(ch) ?? 0) + 1);
    }
    expect(counts.size).toBe(31);
    const expected = total / 31;
    for (const n of counts.values()) expect(Math.abs(n - expected) / expected).toBeLessThan(0.2);
  });

  it("normalises lowercase, spaces and hyphens", () => {
    expect(normaliseCode("abcd-2345")).toBe("ABCD2345");
    expect(normaliseCode("  ab cd 23 45 ")).toBe("ABCD2345");
  });

  it("drops characters outside the alphabet and truncates", () => {
    expect(normaliseCode("AB0CD1IL")).toBe("ABCD");
    expect(normaliseCode("ABCD2345EXTRA")).toBe("ABCD2345");
  });

  it("extracts the code from a pasted deep link or share link (Android gets this wrong)", () => {
    expect(normaliseCode("dermoai://invite/ABCD2345")).toBe("ABCD2345");
    expect(normaliseCode("DERMOAI://INVITE/abcd2345")).toBe("ABCD2345");
    expect(normaliseCode("https://dermoai.example.com/pair/ABCD2345")).toBe("ABCD2345");
    expect(normaliseCode("http://localhost:3000/pair/ABCD-2345")).toBe("ABCD2345");
  });

  it("isCompleteCode rejects short and out-of-alphabet input", () => {
    expect(isCompleteCode("ABCD234")).toBe(false);
    expect(isCompleteCode("ABCD2340")).toBe(false);
    expect(isCompleteCode("ABCD2345")).toBe(true);
  });

  it("QR payload is the deep link the in-app scanner requires", () => {
    expect(deepLink("ABCD2345")).toBe("dermoai://invite/ABCD2345");
    expect(deepLink("ABCD2345").startsWith(DEEP_LINK_PREFIX)).toBe(true);
  });

  it("display form is hyphenated, but only for complete codes", () => {
    expect(formatCodeForDisplay("ABCD2345")).toBe("ABCD-2345");
    expect(formatCodeForDisplay("ABC")).toBe("ABC");
  });
});
