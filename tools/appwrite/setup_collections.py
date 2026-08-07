#!/usr/bin/env python3
"""Provision the Appwrite collections DermoAI's sync layer expects.

Run this ONCE per project (it is idempotent, so re-running after a change is
safe and only creates what is missing).

    export APPWRITE_ENDPOINT=https://sgp.cloud.appwrite.io/v1
    export APPWRITE_PROJECT_ID=...
    export APPWRITE_DATABASE_ID=...
    export APPWRITE_API_KEY=...          # needs the Databases scopes
    python tools/appwrite/setup_collections.py

## Why the key comes from the environment

An Appwrite API key can create, read and delete every document in the project.
It is the one true secret here, and it must never reach the repo, the APK, or a
config file — an APK is readable with `unzip` and `strings`. Endpoint, project
id and database id are NOT secrets: every Appwrite client app ships them, and
the security model assumes the client knows them. Those three live in
`local.properties`; this key lives in your shell for as long as the script runs.

## The schema is defined twice, on purpose

Kotlin reads these names from
`core/data/src/main/kotlin/com/dermoai/core/data/sync/AppwriteSchema.kt`.
This script writes them. Two implementations that disagree about a field name
fail at runtime with a 400 naming an attribute nobody can grep for, so if you
rename anything, rename it in both places and re-run.

Standard library only — no pip install, so it runs anywhere Python 3 does.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

# ── Schema — must match AppwriteSchema.kt exactly ───────────────────────────
# (key, type, size/required-ness). `req=False` throughout: a half-synced row
# from an older app version should degrade to a blank field rather than making
# the whole document unwritable.

STR, INT, FLT, BOOL = "string", "integer", "double", "boolean"

COLLECTIONS = {
    "doctor_profiles": {
        "name": "Doctor profiles",
        "attrs": [
            ("userId", STR, 64), ("fullName", STR, 200),
            ("qualifications", STR, 2000),      # newline-joined, as Room stores it
            ("registrationNumber", STR, 100), ("specialty", STR, 150),
            ("institution", STR, 200), ("yearsExperience", INT, None),
            ("verificationStatus", STR, 20), ("verifiedAt", INT, None),
            ("bio", STR, 2000),
        ],
        "indexes": [("idx_userId", "key", ["userId"])],
    },
    "patient_links": {
        "name": "Patient links",
        "attrs": [
            ("doctorId", STR, 64), ("doctorUserId", STR, 64),
            ("patientUserId", STR, 64), ("patientDisplayName", STR, 200),
            ("linkedAt", INT, None), ("status", STR, 20),
            ("consentGrantedAt", INT, None),
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
            ("doctorId", STR, 64), ("doctorUserId", STR, 64), ("code", STR, 16),
            ("createdAt", INT, None), ("expiresAt", INT, None),
            ("maxUses", INT, None), ("usedCount", INT, None), ("revoked", BOOL, None),
        ],
        # Unique: a redeemed code must identify exactly one doctor, or "who did I
        # just grant access to" has no answer.
        "indexes": [
            ("idx_code_unique", "unique", ["code"]),
            ("idx_doctorUserId", "key", ["doctorUserId"]),
        ],
    },
    "scan_summaries": {
        "name": "Scan summaries",
        # Derived and image-free by design — see AppwriteSchema.kt. No photo, no
        # file path ever leaves the device.
        "attrs": [
            ("patientUserId", STR, 64), ("scanId", STR, 64),
            ("capturedAt", INT, None), ("topLabel", STR, 200),
            ("topLabelCode", STR, 32), ("confidence", FLT, None),
            ("concernBand", STR, 20), ("bodyArea", STR, 100),
        ],
        "indexes": [
            ("idx_patientUserId", "key", ["patientUserId"]),
            ("idx_scanId_unique", "unique", ["scanId"]),
        ],
    },
    "audit_entries": {
        "name": "Audit entries",
        "attrs": [
            ("actorUserId", STR, 64), ("subjectUserId", STR, 64),
            ("action", STR, 32), ("at", INT, None), ("detail", STR, 500),
        ],
        "indexes": [
            ("idx_subjectUserId", "key", ["subjectUserId"]),
            ("idx_actorUserId", "key", ["actorUserId"]),
        ],
    },
}

# Any signed-in user may create documents; per-document permissions written by
# the client then decide who can read each one. Appwrite cannot express
# "readable while an active consented link exists" at the collection level —
# see README.md, which is candid about what this does and does not enforce.
COLLECTION_PERMISSIONS = [
    'create("users")', 'read("users")', 'update("users")', 'delete("users")',
]


_LOCAL_ENV = os.path.join(os.path.dirname(os.path.abspath(__file__)), "local.env")


def _from_local_env():
    """Read tools/appwrite/local.env (gitignored).

    Originally this script took its config from exported environment variables
    only, which is fine on a POSIX shell and a trap on PowerShell — `export` is
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
            f"Easiest fix — put all four in {_LOCAL_ENV} (gitignored),\n"
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
    """Returns (status, parsed_json). Never raises on HTTP error — callers treat
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
        sys.exit(f"ERROR: cannot reach {ENDPOINT} — {e.reason}")


def ok(status):
    # 202 matters: Appwrite creates attributes asynchronously and returns
    # "Accepted", not "Created". Treating it as a failure makes a completely
    # successful run print FAILED against every attribute.
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
    status, body = call("POST", "/databases",
                        {"databaseId": DATABASE, "name": "DermoAI"})
    if status == 404:
        sys.exit(f"ERROR: project {PROJECT} not found at {ENDPOINT}. "
                 "Check APPWRITE_PROJECT_ID and the region in the endpoint.")
    if status == 401:
        sys.exit("ERROR: unauthorised. The API key needs the Databases scopes "
                 "(databases/collections/attributes/indexes, read + write).")
    report(status, f"database {DATABASE}")


def ensure_collection(cid, spec):
    print(f"  {cid}")
    status, _ = call("POST", f"/databases/{DATABASE}/collections", {
        "collectionId": cid, "name": spec["name"],
        "permissions": COLLECTION_PERMISSIONS,
        "documentSecurity": True,   # per-document ACLs are the whole design
    })
    if not report(status, f"collection {cid}"):
        return

    for attr in spec["attrs"]:
        key, kind, size = attr
        base = f"/databases/{DATABASE}/collections/{cid}/attributes"
        if kind == STR:
            st, _ = call("POST", f"{base}/string",
                         {"key": key, "size": size, "required": False, "default": ""})
        elif kind == INT:
            st, _ = call("POST", f"{base}/integer",
                         {"key": key, "required": False, "default": 0})
        elif kind == FLT:
            st, _ = call("POST", f"{base}/float",
                         {"key": key, "required": False, "default": 0.0})
        else:
            st, _ = call("POST", f"{base}/boolean",
                         {"key": key, "required": False, "default": False})
        report(st, f"attr {key} ({kind})")

    # Appwrite builds attributes asynchronously and refuses to index one that is
    # still processing, so give them a moment before creating indexes.
    if spec["indexes"]:
        time.sleep(3)
        for name, kind, cols in spec["indexes"]:
            st, body = call("POST", f"/databases/{DATABASE}/collections/{cid}/indexes",
                            {"key": name, "type": kind, "attributes": cols,
                             "orders": ["ASC"] * len(cols)})
            if st not in (200, 201, 409):
                # Most common cause: the attribute is not 'available' yet.
                time.sleep(5)
                st, body = call("POST",
                                f"/databases/{DATABASE}/collections/{cid}/indexes",
                                {"key": name, "type": kind, "attributes": cols,
                                 "orders": ["ASC"] * len(cols)})
            report(st, f"index {name} ({kind})")


def main():
    print(f"Appwrite setup\n  endpoint {ENDPOINT}\n  project  {PROJECT}\n"
          f"  database {DATABASE}\n")
    ensure_database()
    for cid, spec in COLLECTIONS.items():
        ensure_collection(cid, spec)
    print("\nDone. Re-running is safe — everything above is create-if-missing.")
    print("If any line says FAILED, fix it and re-run; the rest is left alone.")


if __name__ == "__main__":
    main()
