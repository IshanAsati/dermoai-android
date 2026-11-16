import type { RawDoc } from "./schema";

/**
 * The only surface the data layer needs from Appwrite. A seam rather than an
 * abstraction for its own sake: it lets every rule in `repo.ts` run in unit
 * tests against an in-memory store, and keeps SDK call shapes in one place.
 *
 * `queries` are Appwrite query strings (`Query.equal(...)`), passed through.
 */
export interface Db {
  list(collection: string, queries?: string[]): Promise<RawDoc[]>;
  create(collection: string, id: string, data: Record<string, unknown>, permissions: string[]): Promise<RawDoc>;
  update(collection: string, id: string, data: Record<string, unknown>): Promise<RawDoc>;
}

/** Appwrite caps a page at 100 documents. */
export const MAX_PAGE_SIZE = 100;

export class DbError extends Error {
  constructor(
    message: string,
    readonly code: number | undefined,
    readonly type: string | undefined,
  ) {
    super(message);
    this.name = "DbError";
  }

  /** The API key lacks a scope (e.g. `documents.read`). A deployment problem, not a user error. */
  get isMissingScope(): boolean {
    return this.type === "general_unauthorized_scope";
  }

  /** Unique-index violation, e.g. an invite code collision. */
  get isConflict(): boolean {
    return this.code === 409;
  }
}
