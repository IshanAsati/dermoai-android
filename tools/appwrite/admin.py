#!/usr/bin/env python3
"""Appwrite admin helper — inspect and repair the DermoAI backend.

Reads credentials from `tools/appwrite/local.env` (gitignored) so you can run
admin commands without re-exporting environment variables every session, and
without the key ever reaching the repo, the APK, or a chat window.

    tools/appwrite/local.env
        APPWRITE_ENDPOINT=https://sgp.cloud.appwrite.io/v1
        APPWRITE_PROJECT_ID=...
        APPWRITE_DATABASE_ID=...
        APPWRITE_API_KEY=...

Usage:
    python tools/appwrite/admin.py status              # what exists, is it healthy
    python tools/appwrite/admin.py docs <collection>   # list documents
    python tools/appwrite/admin.py verify-doctor <userId>
    python tools/appwrite/admin.py harden              # remove collection-wide read/update/delete
    python tools/appwrite/admin.py wipe <collection>   # delete all docs (asks first)

The key is never printed, never logged, and never passed on a command line
(where it would land in shell history and `ps` output).
"""
import json
import os
import sys
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ENV_FILE = os.path.join(HERE, "local.env")


def load_env():
    """local.env first, real environment second — so an exported value can
    override the file without editing it."""
    values = {}
    if os.path.exists(ENV_FILE):
        for line in open(ENV_FILE, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            values[k.strip()] = v.strip().strip('"').strip("'")
    for k in ("APPWRITE_ENDPOINT", "APPWRITE_PROJECT_ID",
              "APPWRITE_DATABASE_ID", "APPWRITE_API_KEY"):
        if os.environ.get(k):
            values[k] = os.environ[k].strip()
    missing = [k for k in ("APPWRITE_ENDPOINT", "APPWRITE_PROJECT_ID",
                           "APPWRITE_DATABASE_ID", "APPWRITE_API_KEY")
               if not values.get(k)]
    if missing:
        sys.exit(
            f"ERROR: missing {', '.join(missing)}.\n"
            f"Create {ENV_FILE} with one KEY=value per line "
            f"(see local.env.example). It is gitignored."
        )
    return values


ENV = load_env()
ENDPOINT = ENV["APPWRITE_ENDPOINT"].rstrip("/")
PROJECT = ENV["APPWRITE_PROJECT_ID"]
DATABASE = ENV["APPWRITE_DATABASE_ID"]
_KEY = ENV["APPWRITE_API_KEY"]          # underscore: never print this


def call(method, path, body=None):
    req = urllib.request.Request(
        f"{ENDPOINT}{path}",
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
        headers={"Content-Type": "application/json",
                 "X-Appwrite-Project": PROJECT, "X-Appwrite-Key": _KEY},
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read() or "{}")
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw or "{}")
        except json.JSONDecodeError:
            return e.code, {"message": raw.decode(errors="replace")[:200]}
    except urllib.error.URLError as e:
        sys.exit(f"ERROR: cannot reach {ENDPOINT} — {e.reason}")


def cmd_status():
    print(f"endpoint {ENDPOINT}\nproject  {PROJECT}\ndatabase {DATABASE}\n")
    st, body = call("GET", f"/tablesdb/{DATABASE}/tables")
    if st == 401:
        sys.exit("ERROR: 401 — the API key lacks the Databases scopes.")
    if st == 404:
        sys.exit("ERROR: 404 — project or database id is wrong "
                 "(check the region in the endpoint).")
    if st != 200:
        sys.exit(f"ERROR: http {st} — {body.get('message')}")

    cols = body.get("tables", [])
    if not cols:
        print("NO TABLES. Run: python tools/appwrite/setup_collections.py")
        return
    print(f"{'table':20s} {'cols':>5s} {'idx':>4s}  column status")
    for c in cols:
        attrs = c.get("columns", [])
        # A column stuck in 'processing' silently breaks writes that use it,
        # and its index will have failed to build. Surface it rather than let it
        # show up later as a 400 nobody can explain.
        bad = [a for a in attrs if a.get("status") != "available"]
        note = "ok" if not bad else \
            "NOT READY: " + ", ".join(f"{a['key']}={a.get('status')}" for a in bad[:4])
        print(f"{c['name'][:20]:20s} {len(attrs):5d} {len(c.get('indexes', [])):4d}  {note}")


def cmd_docs(collection, limit=25):
    st, body = call("GET",
                    f"/tablesdb/{DATABASE}/tables/{collection}/rows?queries[]="
                    + urllib.parse.quote(json.dumps({"method": "limit", "values": [limit]})))
    if st != 200:
        sys.exit(f"ERROR: http {st} — {body.get('message')}")
    docs = body.get("rows", [])
    print(f"{body.get('total', len(docs))} row(s) in {collection}\n")
    for d in docs:
        shown = {k: v for k, v in d.items() if not k.startswith("$")}
        print(f"  {d['$id']}  {json.dumps(shown, ensure_ascii=False)[:160]}")


def cmd_verify_doctor(user_id):
    """Flip a doctor to VERIFIED in Appwrite.

    NOTE: the app gates the dashboard on the LOCAL Room row, so this alone does
    not unlock it unless a sync pull has run. To unlock immediately, update the
    device directly — see the adb/sqlite command in the README.
    """
    st, body = call("GET",
                    f"/tablesdb/{DATABASE}/tables/doctor_profiles/rows?queries[]="
                    + urllib.parse.quote(json.dumps(
                        {"method": "equal", "attribute": "userId", "values": [user_id]})))
    if st != 200:
        sys.exit(f"ERROR: http {st} — {body.get('message')}")
    docs = body.get("rows", [])
    if not docs:
        sys.exit(f"No doctor_profiles row with userId={user_id}. "
                 "Has the app synced yet?")
    import time
    for d in docs:
        st, resp = call("PATCH",
                        f"/tablesdb/{DATABASE}/tables/doctor_profiles/rows/{d['$id']}",
                        {"data": {"verificationStatus": "VERIFIED",
                                  "verifiedAt": int(time.time() * 1000)}})
        print(("  verified " if st == 200 else f"  FAILED {st} ")
              + f"{d['$id']} ({d.get('fullName', '?')})")


def cmd_wipe(collection):
    st, body = call("GET",
                    f"/tablesdb/{DATABASE}/tables/{collection}/rows")
    docs = body.get("rows", [])
    if not docs:
        print("nothing to delete")
        return
    if input(f"Delete {len(docs)} row(s) from {collection}? [y/N] ").lower() != "y":
        print("aborted")
        return
    for d in docs:
        st, _ = call("DELETE",
                     f"/tablesdb/{DATABASE}/tables/{collection}/rows/{d['$id']}")
        print(("  deleted " if st in (200, 204) else f"  FAILED {st} ") + d["$id"])


def cmd_harden():
    """Make per-row permissions authoritative on tables created by older setups.

    Older setups granted read/update/delete to every signed-in user at the
    TABLE level. Appwrite ORs table and row permissions, so that silently
    overrode every per-row permission. This leaves create("users") only.
    Needs the key's tables.read + tables.write scopes. Rows are not touched.
    """
    wanted = ['create("users")']
    changes = []
    for cid in ("doctor_profiles", "patient_links", "doctor_invites",
                "scan_summaries", "audit_entries"):
        st, col = call("GET", f"/tablesdb/{DATABASE}/tables/{cid}")
        if st != 200:
            print(f"  cannot read {cid}: {st} {col.get('message', '')}")
            continue
        have = sorted(col.get("$permissions", []))
        if have == sorted(wanted) and col.get("rowSecurity"):
            print(f"  ok       {cid}")
        else:
            changes.append((cid, col, have))
            print(f"  CHANGE   {cid}: {have} -> {wanted}")
    if not changes:
        print("nothing to change")
        return
    if input(f"Apply to {len(changes)} table(s)? Rows are not touched. [y/N] ").lower() != "y":
        print("aborted")
        return
    for cid, col, _ in changes:
        st, body = call("PUT", f"/tablesdb/{DATABASE}/tables/{cid}", {
            "name": col["name"], "permissions": wanted,
            "rowSecurity": True, "enabled": col.get("enabled", True),
        })
        print(("  hardened " if st == 200 else f"  FAILED {st} {body.get('message', '')} ") + cid)


if __name__ == "__main__":
    import urllib.parse
    args = sys.argv[1:]
    if not args or args[0] == "status":
        cmd_status()
    elif args[0] == "harden":
        cmd_harden()
    elif args[0] == "docs" and len(args) > 1:
        cmd_docs(args[1])
    elif args[0] == "verify-doctor" and len(args) > 1:
        cmd_verify_doctor(args[1])
    elif args[0] == "wipe" and len(args) > 1:
        cmd_wipe(args[1])
    else:
        sys.exit(__doc__)
