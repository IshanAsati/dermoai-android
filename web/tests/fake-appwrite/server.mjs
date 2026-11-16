// A small Appwrite stand-in for tests. It implements only the endpoints this app
// uses, and enforces the behaviours that were verified against the real service
// (see tests/contract/appwrite-contract.mjs, which runs against both):
//   - a session may only name ITSELF in a document ACL
//   - read/update need a matching ACL entry; `users` covers any signed-in session
//   - an API key without a documents.* scope is refused with general_unauthorized_scope
//   - doctor_invites.code is a unique index
//   - labels are only settable with the key and are returned by GET /account
import { createServer } from "node:http";
import { randomBytes } from "node:crypto";
import { pathToFileURL } from "node:url";

const VERSION = "fake-1.0";
const DEFAULT_SCOPES = ["users.read", "users.write", "sessions.write", "tables.read", "tables.write", "rows.read", "rows.write"]; // mirrors the real key

export function startFakeAppwrite({ port = 0, projectId = "testproject", databaseId = "testdb", keyScopes = DEFAULT_SCOPES } = {}) {
  const state = { users: new Map(), sessions: new Map(), docs: new Map(), keyScopes: [...keyScopes], seq: 0, requests: [], failCreates: new Set() };
  const id = () => (++state.seq).toString(16).padStart(6, "0") + randomBytes(7).toString("hex");
  const iso = () => new Date().toISOString();

  const fail = (res, code, type, message) => send(res, code, { message, code, type, version: VERSION });
  function send(res, code, body) {
    res.writeHead(code, { "content-type": "application/json" });
    res.end(JSON.stringify(body));
  }
  const readBody = (req) =>
    new Promise((resolve) => {
      let raw = "";
      req.on("data", (c) => (raw += c));
      req.on("end", () => {
        try { resolve(raw ? JSON.parse(raw) : {}); } catch { resolve({}); }
      });
    });

  const userView = (u) => ({ $id: u.id, $createdAt: u.createdAt, name: u.name, email: u.email, labels: [...u.labels], status: true });

  function identity(req) {
    const key = req.headers["x-appwrite-key"];
    if (key) return { kind: "key" };
    const secret = req.headers["x-appwrite-session"];
    const s = secret && state.sessions.get(secret);
    if (s) return { kind: "user", userId: s.userId };
    return { kind: "guest" };
  }
  const hasScope = (who, scope) => who.kind === "key" && state.keyScopes.includes(scope);

  function coll(name) {
    let c = state.docs.get(name);
    if (!c) state.docs.set(name, (c = new Map()));
    return c;
  }
  const perm = (action, role) => `${action}("${role}")`;
  const canDo = (who, doc, action) => {
    const p = doc.$permissions;
    if (who.kind === "user") return p.includes(perm(action, "users")) || p.includes(perm(action, `user:${who.userId}`)) || p.includes(perm(action, "any"));
    if (who.kind === "guest") return p.includes(perm(action, "any"));
    return false;
  };
  const docView = (d) => ({ ...d.data, $id: d.$id, $createdAt: d.$createdAt, $updatedAt: d.$updatedAt, $permissions: d.$permissions, $collectionId: d.$collectionId, $tableId: d.$collectionId, $databaseId: databaseId });

  function validPermissions(who, perms) {
    for (const p of perms) {
      const m = /^(read|update|delete|write|create)\("([^"]+)"\)$/.exec(p);
      if (!m) return false;
      const role = m[2];
      if (role === "any" || role === "users" || role === "guests") continue;
      if (who.kind === "key") continue; // a privileged writer may name any account
      if (who.kind === "user" && (role === `user:${who.userId}` || role === `user:${who.userId}/unverified`)) continue;
      return false;
    }
    return true;
  }

  function runQueries(rows, rawQueries) {
    let out = rows;
    let limit = 25;
    for (const raw of rawQueries) {
      let q;
      try { q = JSON.parse(raw); } catch { continue; }
      if (q.method === "equal") out = out.filter((d) => q.values.includes(d.data[q.attribute]));
      else if (q.method === "limit") limit = Number(q.values[0]);
    }
    return out.slice(0, limit);
  }

  const server = createServer(async (req, res) => {
    const url = new URL(req.url, "http://x");
    const path = url.pathname.replace(/^\/v1/, "");
    state.requests.push(`${req.method} ${path}`);

    // ── test control ───────────────────────────────────────────────────────
    if (path.startsWith("/__fake/")) {
      const body = await readBody(req);
      if (path === "/__fake/reset") { state.users.clear(); state.sessions.clear(); state.docs.clear(); state.requests.length = 0; state.failCreates.clear(); return send(res, 200, {}); }
      if (path === "/__fake/fail-creates") { if (body.collection) state.failCreates.add(body.collection); else state.failCreates.clear(); return send(res, 200, { failing: [...state.failCreates] }); }
      if (path === "/__fake/key-scopes") { state.keyScopes = body.scopes; return send(res, 200, { scopes: state.keyScopes }); }
      if (path === "/__fake/requests") return send(res, 200, { requests: state.requests });
      if (path.startsWith("/__fake/docs/")) return send(res, 200, { docs: [...coll(path.split("/").pop()).values()].map(docView) });
      return fail(res, 404, "general_route_not_found", "no such control route");
    }

    if (req.headers["x-appwrite-project"] !== projectId) return fail(res, 404, "project_not_found", "Project not found");
    const who = identity(req);
    const body = ["POST", "PUT", "PATCH", "DELETE"].includes(req.method) ? await readBody(req) : {};

    if (path === "/health/version") return send(res, 200, { version: VERSION });

    // ── account ────────────────────────────────────────────────────────────
    if (path === "/account" && req.method === "POST") {
      if ([...state.users.values()].some((u) => u.email === body.email)) return fail(res, 409, "user_already_exists", "A user with the same id, email, or phone already exists in this project.");
      if (typeof body.password !== "string" || body.password.length < 8) return fail(res, 400, "password_recently_used", "Invalid `password` param: Password must be between 8 and 256 characters long.");
      const u = { id: body.userId === "unique()" ? id() : body.userId, email: body.email, password: body.password, name: body.name ?? "", labels: [], createdAt: iso() };
      state.users.set(u.id, u);
      return send(res, 201, userView(u));
    }
    if (path === "/account" && req.method === "GET") {
      if (who.kind !== "user") return fail(res, 401, "general_unauthorized_scope", "User (role: guests) missing scopes (account)");
      return send(res, 200, userView(state.users.get(who.userId)));
    }
    if (path === "/account/sessions/email" && req.method === "POST") {
      const u = [...state.users.values()].find((x) => x.email === body.email && x.password === body.password);
      if (!u) return fail(res, 401, "user_invalid_credentials", "Invalid credentials. Please check the email and password.");
      return send(res, 201, makeSession(u.id, who.kind === "key"));
    }
    if (path === "/account/sessions/anonymous" && req.method === "POST") {
      const u = { id: id(), email: "", password: "", name: "", labels: [], createdAt: iso(), anonymous: true };
      state.users.set(u.id, u);
      return send(res, 201, makeSession(u.id, true));
    }
    if (path === "/account/sessions/current" && req.method === "DELETE") {
      const secret = req.headers["x-appwrite-session"];
      if (!state.sessions.delete(secret)) return fail(res, 401, "user_session_not_found", "Session not found");
      return send(res, 204, {});
    }

    // ── users (admin) ──────────────────────────────────────────────────────
    let m;
    if (path === "/users" && req.method === "GET") {
      if (!hasScope(who, "users.read")) return scopeFail(res, who, "users.read");
      const email = (url.searchParams.getAll("queries[]").concat(url.searchParams.getAll("queries[0]")).map(safeJson).find((q) => q?.method === "equal" && q.attribute === "email"))?.values?.[0];
      const users = [...state.users.values()].filter((u) => !email || u.email === email);
      return send(res, 200, { total: users.length, users: users.map(userView) });
    }
    if ((m = /^\/users\/([^/]+)\/labels$/.exec(path)) && req.method === "PUT") {
      if (!hasScope(who, "users.write")) return scopeFail(res, who, "users.write");
      const u = state.users.get(m[1]);
      if (!u) return fail(res, 404, "user_not_found", "User with the requested ID could not be found.");
      if (!Array.isArray(body.labels) || body.labels.some((l) => !/^[A-Za-z0-9]{1,36}$/.test(l))) return fail(res, 400, "general_argument_invalid", "Invalid `labels` param: labels must be alphanumeric.");
      u.labels = body.labels;
      return send(res, 200, userView(u));
    }
    if ((m = /^\/users\/([^/]+)\/sessions$/.exec(path)) && req.method === "POST") {
      if (!hasScope(who, "sessions.write")) return scopeFail(res, who, "sessions.write");
      if (!state.users.has(m[1])) return fail(res, 404, "user_not_found", "User not found");
      return send(res, 201, makeSession(m[1], true));
    }

    // ── documents ──────────────────────────────────────────────────────────
    // Two route families over ONE store, as in real Appwrite: the legacy Databases API
    // (collections/documents, used by the Android SDK) and TablesDB (tables/rows, used by the web
    // app). They need different key scopes: documents.* versus rows.*.
    if ((m = /^\/(?:databases\/([^/]+)\/collections|tablesdb\/([^/]+)\/tables)\/([^/]+)\/(documents|rows)(?:\/([^/]+))?$/.exec(path))) {
      const legacy = m[4] === "documents";
      const db = m[1] ?? m[2];
      const cname = m[3];
      const docId = m[5];
      const readScope = legacy ? "documents.read" : "rows.read";
      const writeScope = legacy ? "documents.write" : "rows.write";
      const listKey = legacy ? "documents" : "rows";
      const idField = legacy ? "documentId" : "rowId";
      if (db !== databaseId) return fail(res, 404, "database_not_found", "Database not found");
      const c = coll(cname);
      const needsKeyScope = (s) => who.kind === "key" && !state.keyScopes.includes(s);

      if (req.method === "GET" && !docId) {
        if (needsKeyScope(readScope)) return scopeFail(res, who, readScope);
        const raw = [...url.searchParams.entries()].filter(([k]) => k.startsWith("queries")).map(([, v]) => v);
        const readable = [...c.values()].filter((d) => who.kind === "key" || canDo(who, d, "read"));
        const rows = runQueries(readable, raw);
        return send(res, 200, { total: rows.length, [listKey]: rows.map(docView) });
      }
      if (req.method === "POST" && !docId) {
        if (state.failCreates.has(cname)) return fail(res, 500, "general_server_error", "Server Error");
        if (needsKeyScope(writeScope)) return scopeFail(res, who, writeScope);
        if (who.kind === "guest") return fail(res, 401, "user_unauthorized", "The current user is not authorized to perform the requested action.");
        const perms = body.permissions ?? [];
        if (!validPermissions(who, perms)) return fail(res, 401, "user_unauthorized", "Permissions must be one of: (any, users, user:<self>)");
        const newId = body[idField] === "unique()" ? id() : body[idField];
        if (c.has(newId)) return fail(res, 409, "document_already_exists", "Document with the requested ID already exists.");
        if (cname === "doctor_invites" && [...c.values()].some((d) => d.data.code === body.data?.code)) return fail(res, 409, "document_already_exists", "Document with the requested ID already exists. Try again with a different ID or use ID.unique() to generate a unique ID.");
        const d = { $id: newId, $createdAt: iso(), $updatedAt: iso(), $permissions: perms, $collectionId: cname, data: { ...body.data } };
        c.set(newId, d);
        return send(res, 201, docView(d));
      }
      if (req.method === "PATCH" && docId) {
        if (needsKeyScope(writeScope)) return scopeFail(res, who, writeScope);
        const d = c.get(docId);
        if (!d) return fail(res, 404, "document_not_found", "Document with the requested ID could not be found.");
        if (who.kind !== "key" && !canDo(who, d, "update")) return fail(res, 401, "user_unauthorized", "The current user is not authorized to perform the requested action.");
        if (body.permissions && !validPermissions(who, body.permissions)) return fail(res, 401, "user_unauthorized", "Permissions must be one of: (any, users, user:<self>)");
        d.data = { ...d.data, ...(body.data ?? {}) };
        if (body.permissions) d.$permissions = body.permissions;
        d.$updatedAt = iso();
        return send(res, 200, docView(d));
      }
      if (req.method === "DELETE" && docId) {
        const d = c.get(docId);
        if (!d) return fail(res, 404, "document_not_found", "Document not found");
        if (who.kind !== "key" && !canDo(who, d, "delete")) return fail(res, 401, "user_unauthorized", "The current user is not authorized to perform the requested action.");
        c.delete(docId);
        return send(res, 204, {});
      }
    }
    return fail(res, 404, "general_route_not_found", `No route ${req.method} ${path}`);
  });

  function makeSession(userId, includeSecret) {
    const secret = randomBytes(24).toString("hex");
    state.sessions.set(secret, { userId });
    return { $id: id(), userId, secret: includeSecret ? secret : "", provider: "email", expire: new Date(Date.now() + 86_400_000).toISOString() };
  }
  const scopeFail = (res, who, scope) => fail(res, 401, "general_unauthorized_scope", `app.${projectId}@service (role: applications) missing scopes (["${scope}"])`);
  const safeJson = (s) => { try { return JSON.parse(s); } catch { return null; } };

  return new Promise((resolve) => {
    server.listen(port, "127.0.0.1", () => {
      const actual = server.address().port;
      resolve({
        url: `http://127.0.0.1:${actual}/v1`,
        projectId,
        databaseId,
        state,
        close: () => new Promise((r) => server.close(() => r())),
      });
    });
  });
}

// Run directly: `node tests/fake-appwrite/server.mjs [port]`
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.argv[2] ?? 4010);
  const s = await startFakeAppwrite({ port });
  console.log(`fake appwrite on ${s.url} project=${s.projectId} db=${s.databaseId}`);
}
