import { type Db, DbError } from "@/lib/data/db";
import type { RawDoc } from "@/lib/data/schema";

type Parsed = { method: string; attribute?: string; values: unknown[] };

/**
 * In-memory stand-in for Appwrite supporting what the repo uses: `equal` and
 * `limit` queries, a unique index on `doctor_invites.code`, and server-assigned
 * `$createdAt`. Anything else throws, so a test cannot silently rely on a
 * query the fake does not implement.
 */
export class MemoryDb implements Db {
  readonly store = new Map<string, Map<string, RawDoc>>();
  readonly writes: Array<{ op: "create" | "update"; collection: string; id: string }> = [];
  constructor(private clock: () => number = () => Date.now()) {}

  private coll(name: string) {
    let c = this.store.get(name);
    if (!c) this.store.set(name, (c = new Map()));
    return c;
  }

  seed(collection: string, doc: RawDoc) {
    this.coll(collection).set(doc.$id, doc);
  }

  async list(collection: string, queries: string[] = []): Promise<RawDoc[]> {
    let docs = [...this.coll(collection).values()];
    let limit = Infinity;
    for (const raw of queries) {
      const q = JSON.parse(raw) as Parsed;
      if (q.method === "equal") {
        docs = docs.filter((d) => q.values.includes(d[q.attribute as string]));
      } else if (q.method === "limit") {
        limit = Number(q.values[0]);
      } else {
        throw new Error(`MemoryDb does not implement query "${q.method}"`);
      }
    }
    return docs.slice(0, limit).map((d) => ({ ...d }));
  }

  async create(collection: string, id: string, data: Record<string, unknown>, permissions: string[]): Promise<RawDoc> {
    const c = this.coll(collection);
    if (c.has(id)) throw new DbError("exists", 409, "document_already_exists");
    if (collection === "doctor_invites" && [...c.values()].some((d) => d.code === data.code)) {
      throw new DbError("unique", 409, "document_already_exists");
    }
    const doc: RawDoc = {
      ...data,
      $id: id,
      $permissions: permissions,
      $createdAt: new Date(this.clock()).toISOString(),
    };
    c.set(id, doc);
    this.writes.push({ op: "create", collection, id });
    return { ...doc };
  }

  async update(collection: string, id: string, data: Record<string, unknown>): Promise<RawDoc> {
    const existing = this.coll(collection).get(id);
    if (!existing) throw new DbError("not found", 404, "document_not_found");
    const next = { ...existing, ...data };
    this.coll(collection).set(id, next);
    this.writes.push({ op: "update", collection, id });
    return { ...next };
  }
}
