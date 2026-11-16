import { describe, expect, it } from "vitest";
import { genuineLinks } from "@/lib/data/repo";
import { inviteState, linkFromDoc, type Invite, type RawDoc } from "@/lib/data/schema";
import { findJustPaired, JUST_PAIRED_WINDOW_MS } from "@/lib/domain/pairing-status";

const NOW = Date.UTC(2026, 0, 15, 12);
const DOCTOR = "doc";
const inv = (over: Partial<Invite> = {}): Invite => {
  const createdAt = over.createdAt ?? NOW - 120_000;
  return {
    id: "i1", doctorId: "p", doctorUserId: DOCTOR, code: "ABCD2345",
    createdAt, storedAt: createdAt, expiresAt: NOW + 3_600_000, maxUses: 1, usedCount: 0, revoked: false, ...over,
  };
};
const link = (storedAt: number, over: Record<string, unknown> = {}, author: string | null = "patient-session") =>
  linkFromDoc({
    $id: "l" + storedAt, $createdAt: new Date(storedAt).toISOString(), $updatedAt: new Date(storedAt).toISOString(),
    $permissions: author ? [`read("users")`, `update("user:${author}")`] : [`read("users")`],
    doctorId: "p", doctorUserId: DOCTOR, patientUserId: "pat", patientDisplayName: "Pia", linkedAt: storedAt,
    status: "ACTIVE", consentGrantedAt: storedAt, ...over,
  } as RawDoc);

describe("invite state", () => {
  it("a spent single-use code reads Used, not Cancelled, even though it was auto-revoked", () => {
    expect(inviteState(inv({ usedCount: 1, revoked: true }), NOW)).toBe("USED");
  });
  it("a code the doctor cancelled before anyone used it reads Cancelled", () => {
    expect(inviteState(inv({ revoked: true }), NOW)).toBe("REVOKED");
  });
  it("a spent code that has also expired still reads Used", () => {
    expect(inviteState(inv({ usedCount: 1, expiresAt: NOW - 1 }), NOW)).toBe("USED");
  });
});

describe("genuine pairings (code usage must survive revocation)", () => {
  const invites = [inv()];
  it("counts a revoked link, so ending access cannot re-open a single-use code", () => {
    const revoked = link(NOW - 60_000, { status: "REVOKED" });
    expect(genuineLinks([revoked], invites, DOCTOR)).toHaveLength(1);
  });
  it("still excludes doctor-authored, unconsented, unauthored and out-of-window links", () => {
    expect(genuineLinks([link(NOW - 60_000, {}, DOCTOR)], invites, DOCTOR)).toHaveLength(0);
    expect(genuineLinks([link(NOW - 60_000, { consentGrantedAt: undefined })], invites, DOCTOR)).toHaveLength(0);
    expect(genuineLinks([link(NOW - 60_000, {}, null)], invites, DOCTOR)).toHaveLength(0);
    expect(genuineLinks([link(NOW + 10 * 3_600_000)], invites, DOCTOR)).toHaveLength(0);
    expect(genuineLinks([link(NOW - 60_000, { doctorUserId: "someone-else" })], invites, DOCTOR)).toHaveLength(0);
  });
});

describe("just-paired announcement", () => {
  it("announces a pairing once the newest code is spent", () => {
    const spent = inv({ usedCount: 1, revoked: true });
    expect(findJustPaired([spent], [link(NOW - 60_000)], NOW)).not.toBeNull();
  });
  it("announces nothing while the code still has uses left", () => {
    expect(findJustPaired([inv({ maxUses: 3, usedCount: 1 })], [link(NOW - 60_000)], NOW)).toBeNull();
  });
  it("announces nothing for a code cancelled before use", () => {
    expect(findJustPaired([inv({ revoked: true })], [], NOW)).toBeNull();
  });
  it("announces nothing once the pairing is old news", () => {
    const spent = inv({ usedCount: 1, revoked: true });
    expect(findJustPaired([spent], [link(NOW - JUST_PAIRED_WINDOW_MS - 1)], NOW)).toBeNull();
  });
  it("picks the newest pairing and ignores links from before the code existed", () => {
    const spent = inv({ usedCount: 1, revoked: true });
    const before = link(NOW - 10 * 3_600_000);
    const recent = link(NOW - 30_000);
    expect(findJustPaired([spent], [before, recent], NOW)?.storedAt).toBe(recent.storedAt);
    expect(findJustPaired([spent], [before], NOW)).toBeNull();
  });
  it("only looks at the newest code, not an older spent one", () => {
    const old = inv({ id: "old", createdAt: NOW - 900_000, usedCount: 1, revoked: true });
    const open = inv({ id: "new", createdAt: NOW - 60_000 });
    expect(findJustPaired([old, open], [link(NOW - 700_000)], NOW)).toBeNull();
  });
  it("handles no invites and no links", () => {
    expect(findJustPaired([], [], NOW)).toBeNull();
  });
});
