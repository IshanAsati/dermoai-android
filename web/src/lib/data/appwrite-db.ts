import "server-only";
import { AppwriteException, type TablesDB } from "node-appwrite";
import { type Db, DbError } from "./db";
import type { RawDoc } from "./schema";

function wrap(error: unknown): never {
  if (error instanceof AppwriteException) {
    throw new DbError(error.message, error.code, error.type);
  }
  throw error;
}

/**
 * Appwrite-backed {@link Db}. Which identity it acts as is decided by the client
 * it is given. The rest of the app speaks "collection/document"; Appwrite now
 * calls those tables and rows, and this is the only file that knows.
 */
export function appwriteDb(tablesDb: TablesDB, databaseId: string): Db {
  return {
    async list(collection, queries = []) {
      try {
        const res = await tablesDb.listRows({ databaseId, tableId: collection, queries });
        return res.rows as unknown as RawDoc[];
      } catch (e) {
        return wrap(e);
      }
    },
    async create(collection, id, data, permissions) {
      try {
        const row = await tablesDb.createRow({ databaseId, tableId: collection, rowId: id, data, permissions });
        return row as unknown as RawDoc;
      } catch (e) {
        return wrap(e);
      }
    },
    async update(collection, id, data) {
      try {
        const row = await tablesDb.updateRow({ databaseId, tableId: collection, rowId: id, data });
        return row as unknown as RawDoc;
      } catch (e) {
        return wrap(e);
      }
    },
  };
}
