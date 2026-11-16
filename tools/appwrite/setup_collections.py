#!/usr/bin/env python3
"""Provision the Appwrite tables DermoAI's sync layer expects.

Run this ONCE per project (it is idempotent, so re-running after a change is
safe and only creates what is missing).

    export APPWRITE_ENDPOINT=https://sgp.cloud.appwrite.io/v1
    export APPWRITE_PROJECT_ID=...
    export APPWRITE_DATABASE_ID=...
    export APPWRITE_API_KEY=...          # needs databases/tables/columns/indexes read + write
    python tools/appwrite/setup_collections.py

## Appwrite's naming

Appwrite renamed its Databases API: collections are now TABLES, documents are
ROWS and attributes are COLUMNS (endpoints under `/tablesdb`, key scopes
`tables.*`, `rows.*`, `columns.*`). This script uses the new API. The file keeps
its old name so existing docs and muscle memory still find it; the Android SDK
still speaks the old endpoints and reads the same data, so nothing else changes.

## Why the key comes from the environment

An Appwrite API key can create, read and delete every row in the project.
It is the one true secret here, and it must never reach the repo, the APK, or a
config file - an APK is readable with `unzip` and `strings`. Endpoint, project
id and database id are NOT secrets: every Appwrite client app ships them, and
the security model assumes the client knows them. Those three live in
`local.properties`; this key lives in your shell for as long as the script runs.

## The schema is defined twice, on purpose

Kotlin reads these names from
`core/data/src/main/kotlin/com/dermoai/core/data/sync/AppwriteSchema.kt`.
This script writes them. Two implementations that disagree about a column name
fail at runtime with a 400 naming a column nobody can grep for, so if you
rename anything, rename it in both places and re-run.

## Two things that are easy to get wrong

* **Timestamps are `bigint`.** In the TablesDB API `integer` is 32-bit
  (about 2.1 billion), and epoch milliseconds are around 1.7 trillion. An
  `integer` column would reject every timestamp. Counts stay `integer`.
* **Table permissions are create-only.** Appwrite ORs table-level and row-level
  permissions, so granting read/update/delete to "users" at the table level
  overrides every per-row permission and lets any signed-in session (including
  the anonymous ones the app mints per install) read and edit every patient's
  data. See `COLLECTION_PERMISSIONS`.

Standard library only - no pip install, so it runs anywhere Python 3 does.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

# -- Schema - must match AppwriteSchema.kt exactly ---------------------------
# (key, type, size). Nothing is `required`: a half-synced row from an older app
# version should degrade to a blank field rather than making the whole row
# unwritable.

# VARCHAR is fully indexable below 768 chars; TEXT holds long free text.
VARCHAR, TEXT, INT, BIGINT, FLT, BOOL = "varchar", "text", "integer", "bigint", "float", "boolean"

COLLECTIONS = {
    "doctor_profiles": {
        "name": "Doctor profiles",
        "attrs": [
            ("userId", VARCHAR, 64), ("fullName", VARCHAR, 200),
            ("qualifications", TEXT, None),      # newline-joined, as Room stores it
            ("registrationNumber", VARCHAR, 100), ("specialty", VARCHAR, 150),
            ("institution", VARCHAR, 200), ("yearsExperience", INT, None),
            ("verificationStatus", VARCHAR, 20), ("verifiedAt", BIGINT, None),
            ("bio", TEXT, None),
        ],
        "indexes": [("idx_userId", "key", ["userId"])],
    },
    "patient_links": {
        "name": "Patient links",
        "attrs": [
            ("doctorId", VARCHAR, 64), ("doctorUserId", VARCHAR, 64),
            ("patientUserId", VARCHAR, 64), ("patientDisplayName", VARCHAR, 200),
            ("linkedAt", BIGINT, None), ("status", VARCHAR, 20),
            ("consentGrantedAt", BIGINT, None),
        ],
        # Not unique: a link revoked and later re-granted reuses the pair, and a
        # unique index would reject the re-grant.
        "indexes": [
            ("idx_doctorUserId", "key", ["doctorUserId"]),
            ("idx_patientUserId", "key", ["patientUserId"]),
        ],
    },
    "doctor_invites": {
        "name": "Doctor invites",
        "attrs": [
            ("doctorId", VARCHAR, 64), ("doctorUserId", VARCHAR, 64), ("code", VARCHAR, 16),
            ("createdAt", BIGINT, None), ("expiresAt", BIGINT, None),
            ("maxUses", INT, None), ("usedCount", INT, None), ("revoked", BOOL, None),
        ],
        # Unique: a redeemed code must identify exactly one doctor, or "who did I
        # just share my scans with" has no answer.
        "indexes": [
            ("idx_code_unique", "unique", ["code"]),
            ("idx_doctorUserId", "key", ["doctorUserId"]),
        ],
    },
    "scan_summaries": {
        "name": "Scan summaries",
        "attrs": [
            ("patientUserId", VARCHAR, 64), ("scanId", VARCHAR, 64),
            ("capturedAt", BIGINT, None), ("topLabel", VARCHAR, 200),
            ("topLabelCode", VARCHAR, 32), ("confidence", FLT, None),
            ("concernBand", VARCHAR, 20), ("bodyArea", VARCHAR, 100),
        ],
        "indexes": [
            ("idx_patientUserId", "key", ["patientUserId"]),
            ("idx_scanId_unique", "unique", ["scanId"]),
        ],
    },
    "audit_entries": {
        "name": "Audit entries",
        "attrs": [
            ("actorUserId", VARCHAR, 64), ("subjectUserId", VARCHAR, 64),
            ("action", VARCHAR, 32), ("at", BIGINT, None), ("detail", VARCHAR, 500),
        ],
        "indexes": [
            ("idx_subjectUserId", "key", ["subjectUserId"]),
            ("idx_actorUserId", "key", ["actorUserId"]),
        ],
    },
}

# Any signed-in user may CREATE a row; what a row allows after that is decided by
# the permissions written on the row itself.
#
# Do NOT add read/update/delete("users") here. Appwrite ORs table-level and
# row-level permissions, so a table-level grant overrides every per-row
# permission: any signed-in session, including the anonymous ones the app
# creates silently for every install, could then read and edit every patient's
# summaries and every doctor's invites. This was measured against a live project.
COLLECTION_PERMISSIONS = [
    'create("users")',
]

_LOCAL_ENV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "local.env")


def _from_local_env():
    """Read tools/appwrite/local.env (gitignored).

    Originally this script took its config from exported environment variables
    only, which is fine on a POSIX shell and a trap on PowerShell - `export` is
    not a cmdlet, so all four assignments fail, and the script then reports the
    first missing variable rather than the real problem. Reading the file makes
    the happy path shell-agnostic; real environment variables still win.
    """
    values = {}
    if os.path.exists(_LOCAL_ENV):
        for line in open(_LOCAL_ENV, encoding="utf-8"):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                values[k.strip()] = v.strip().strip('"').strip("'")
    return values


_FILE_VALUES = _from_local_env()


def env(name, required=True):
    v = (os.environ.get(name) or _FILE_VALUES.get(name) or "").strip()
    if required and not v:
        sys.exit(
            f"ERROR: {name} is not set.\n\n"
            f"Easiest fix - put all four in {_LOCAL_ENV} (gitignored),\n"
            "one KEY=value per line:\n"
            "  APPWRITE_ENDPOINT=https://sgp.cloud.appwrite.io/v1\n"
            "  APPWRITE_PROJECT_ID=...\n"
            "  APPWRITE_DATABASE_ID=...\n"
            "  APPWRITE_API_KEY=...   # never commit this\n"
        )
    return v


ENDPOINT = env("APPWRITE_ENDPOINT").rstrip("/")
PROJECT = env("APPWRITE_PROJECT_ID")
DATABASE = env("APPWRITE_DATABASE_ID")
API_KEY = env("APPWRITE_API_KEY")


def call(method, path, body=None):
    """Returns (status, parsed_json). Never raises on HTTP error - callers treat
    409 as 'already exists', which is what makes this script idempotent."""
    url = f"{ENDPOINT}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Content-Type": "application/json",
        "X-Appwrite-Project": PROJECT,
        "X-Appwrite-Key": API_KEY,
    })
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
        sys.exit(f"ERROR: cannot reach {ENDPOINT} - {e.reason}")


def ok(status):
    # 202 matters: Appwrite creates columns asynchronously and returns
    # "Accepted", not "Created". Treating it as a failure makes a completely
    # successful run print FAILED against every column.
    return status in (200, 201, 202, 204)


def report(status, what):
    if ok(status):
        print(f"    created  {what}")
    elif status == 409:
        print(f"    exists   {what}")
    else:
        print(f"    FAILED   {what}  (http {status})")
    return ok(status) or status == 409


def ensure_database():
    status, _ = call("POST", "/tablesdb", {"databaseId": DATABASE, "name": "DermoAI"})
    if status == 404:
        sys.exit(f"ERROR: project {PROJECT} not found at {ENDPOINT}. "
                 "Check APPWRITE_PROJECT_ID and the region in the endpoint.")
    if status == 401:
        sys.exit("ERROR: unauthorised. The API key needs the Databases scopes "
                 "(databases, tables, columns, indexes: read + write).")
    report(status, f"database {DATABASE}")


def _create_column(table, key, kind, size):
    base = f"/tablesdb/{DATABASE}/tables/{table}/columns"
    optional = {"required": False}
    if kind == VARCHAR:
        return call("POST", f"{base}/varchar", {"key": key, "size": size, "default": "", **optional})
    if kind == TEXT:
        return call("POST", f"{base}/text", {"key": key, "default": "", **optional})
    if kind == INT:
        return call("POST", f"{base}/integer", {"key": key, "default": 0, **optional})
    if kind == BIGINT:
        return call("POST", f"{base}/bigint", {"key": key, "default": 0, **optional})
    if kind == FLT:
        return call("POST", f"{base}/float", {"key": key, "default": 0.0, **optional})
    return call("POST", f"{base}/boolean", {"key": key, "default": False, **optional})


def ensure_collection(cid, spec):
    print(f"  {cid}")
    status, _ = call("POST", f"/tablesdb/{DATABASE}/tables", {
        "tableId": cid, "name": spec["name"],
        "permissions": COLLECTION_PERMISSIONS,
        "rowSecurity": True,   # per-row permissions are the whole design
    })
    if not report(status, f"table {cid}"):
        return

    for key, kind, size in spec["attrs"]:
        st, _ = _create_column(cid, key, kind, size)
        report(st, f"column {key} ({kind})")

    # Appwrite builds columns asynchronously and refuses to index one that is
    # still processing, so give them a moment before creating indexes.
    if spec["indexes"]:
        time.sleep(3)
        for name, kind, cols in spec["indexes"]:
            body = {"key": name, "type": kind, "columns": cols, "orders": ["ASC"] * len(cols)}
            st, _ = call("POST", f"/tablesdb/{DATABASE}/tables/{cid}/indexes", body)
            if st not in (200, 201, 202, 409):
                # Most common cause: the column is not 'available' yet.
                time.sleep(5)
                st, _ = call("POST", f"/tablesdb/{DATABASE}/tables/{cid}/indexes", body)
            report(st, f"index {name} ({kind})")


def main():
    print(f"Appwrite setup\n  endpoint {ENDPOINT}\n  project  {PROJECT}\n"
          f"  database {DATABASE}\n")
    ensure_database()
    for cid, spec in COLLECTIONS.items():
        ensure_collection(cid, spec)
    print("\nDone. Re-running is safe - everything above is create-if-missing.")
    print("If any line says FAILED, fix it and re-run; the rest is left alone.")


if __name__ == "__main__":
    main()
