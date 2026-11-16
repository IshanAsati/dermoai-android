import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

/**
 * Neumorphism's known weakness is contrast. Rather than trust a hand-written
 * list, this reads the tokens out of globals.css and checks the foreground and
 * background pairs the components actually use against WCAG 2.2 AA (4.5:1 for
 * text, 3:1 for large text and UI boundaries).
 */
const css = readFileSync(new URL("../../src/app/globals.css", import.meta.url), "utf8");

function token(name: string): string {
  const m = new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`).exec(css);
  if (!m) throw new Error(`token --${name} not found in globals.css`);
  return m[1];
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function ratio(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const AA_TEXT = 4.5;

// [foreground token, background token, where it is used]
const TEXT_PAIRS: Array<[string, string, string]> = [
  ["ink", "canvas", "body text on page"],
  ["ink", "card", "body text on cards"],
  ["ink", "well", "text in inset wells and inputs"],
  ["slate", "canvas", "secondary text on page"],
  ["slate", "card", "secondary text on cards"],
  ["slate", "well", "secondary text in wells, badge-neutral"],
  ["pine", "canvas", "links and active nav on page"],
  ["pine", "card", "links on cards"],
  ["pine-deep", "canvas", "headings on page"],
  ["pine-deep", "card", "headings and secondary buttons on cards"],
  ["pine-deep", "pine-pale", "badge-pine, avatar, alerts"],
  ["on-pine", "pine", "primary button"],
  ["on-pine", "coral", "critical badge"],
  ["coral-text", "coral-pale", "error alert, badge-worse, danger button"],
  ["coral-text", "card", "field errors"],
  ["amber-text", "amber-pale", "medium badge, warning alert"],
  ["orange-text", "orange-pale", "high badge"],
  ["sage-text", "sage-pale", "low badge, badge-better"],
  ["pine", "pine-pale", "nav icon on active tab"],
];

describe("WCAG AA contrast of the design tokens", () => {
  for (const [fg, bg, where] of TEXT_PAIRS) {
    it(`${fg} on ${bg} (${where}) >= 4.5:1`, () => {
      const r = ratio(token(fg), token(bg));
      expect(r, `${fg}/${bg} is ${r.toFixed(2)}:1`).toBeGreaterThanOrEqual(AA_TEXT);
    });
  }

  it("the documented-bad pairing stays bad, so nobody 'fixes' it by using it", () => {
    // AGENTS.md: Muted #94A3B8 on the base is ~2.3:1 and must never carry text.
    expect(ratio("#94A3B8", token("canvas"))).toBeLessThan(3);
  });

  it("focus ring (pine) is a 3:1 non-text contrast against page and cards", () => {
    expect(ratio(token("pine"), token("canvas"))).toBeGreaterThanOrEqual(3);
    expect(ratio(token("pine"), token("card"))).toBeGreaterThanOrEqual(3);
  });

  it("QR code uses dark pine on pure white for reliable scanning", () => {
    expect(ratio("#123F33", "#FFFFFF")).toBeGreaterThanOrEqual(10);
  });
});
