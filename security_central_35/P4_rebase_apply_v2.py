#!/usr/bin/env python3
"""Re-establish SQLCipher P4 functionally on the pinned Central Room-35 source.

This is deliberately NOT a branch merge. It reconstructs the previously validated
P4 payload, applies only the seven SQLCipher-related file deltas to a clean current
Central source tree, adapts the encryption test fixture to logical schema 35, and
then verifies that no Room schema/migration file changed.
"""
from __future__ import annotations

import argparse
import base64
import gzip
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile

ALLOWED = {
    "app/build.gradle.kts",
    "app/src/androidTest/java/com/fush/erp/backup/DatabaseEncryptionInstrumentedTest.kt",
    "app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt",
    "app/src/main/java/com/fush/erp/backup/DatabaseEncryptionManager.kt",
    "app/src/main/java/com/fush/erp/backup/DatabaseKeyManager.kt",
    "app/src/main/java/com/fush/erp/data/AppContainer.kt",
    "app/src/test/java/com/fush/erp/backup/DatabaseEncryptionManagerTest.kt",
}

DIRECT_GIT_APPLY = {
    "app/build.gradle.kts",
    "app/src/androidTest/java/com/fush/erp/backup/DatabaseEncryptionInstrumentedTest.kt",
    "app/src/main/java/com/fush/erp/backup/DatabaseEncryptionManager.kt",
    "app/src/main/java/com/fush/erp/backup/DatabaseKeyManager.kt",
    "app/src/test/java/com/fush/erp/backup/DatabaseEncryptionManagerTest.kt",
}

SEMANTIC_HUNK_APPLY = {
    "app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt",
    "app/src/main/java/com/fush/erp/data/AppContainer.kt",
}

OLD_PART_SHA256 = {
    "part_00.b64": "0d2c3defbc75167faf8070f6e924a040980ed9ed90e859a740a3955756ba7492",
    "part_01.b64": "1ab44f8e7af7ab86c325907ee2f0a453b8fe348cb0c4e103d013e62ba46e21e5",
    "part_02.b64": "62c2cbdf6b8713213682c3e5308a4109dc93b8628ba63b6ebaef9256679db6ea",
}
OLD_COMBINED_B64_SHA256 = "f9e1aed8eea5c10b051a6a68a426d3f9cc659ee0b3daa736ab2cee59110b6ea5"
OLD_PATCH_SHA256 = "aa2ee3674ac476e00d22fb84595d49d79bf48bd5e43c1b9a9a8bf2acb4caca8e"


def run(*args: str, cwd: Path, input_text: str | None = None) -> str:
    p = subprocess.run(args, cwd=cwd, input=input_text, text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if p.returncode != 0:
        raise RuntimeError(f"command failed ({p.returncode}): {' '.join(args)}\n{p.stdout}")
    return p.stdout


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def reconstruct_old_patch(repo_root: Path) -> str:
    part_dir = repo_root / "security_central_14_5_54" / "P4_sqlcipher_parts"
    chunks: list[bytes] = []
    for name in ("part_00.b64", "part_01.b64", "part_02.b64"):
        raw = (part_dir / name).read_bytes()
        if sha256(raw) != OLD_PART_SHA256[name]:
            raise RuntimeError(f"old P4 payload integrity failure: {name}")
        chunks.append(raw)
    combined = b"".join(chunks)
    if sha256(combined) != OLD_COMBINED_B64_SHA256:
        raise RuntimeError("old P4 combined Base64 integrity failure")
    patch_bytes = gzip.decompress(base64.b64decode(combined))
    if sha256(patch_bytes) != OLD_PATCH_SHA256:
        raise RuntimeError("old P4 decoded patch integrity failure")
    return patch_bytes.decode("utf-8")


def split_patch(patch: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for chunk in patch.split("diff --git ")[1:]:
        first = chunk.splitlines()[0]
        left, _right = first.split(" ", 1)
        path = left[2:]
        result[path] = "diff --git " + chunk
    return result


def verify_baseline(project: Path) -> None:
    db = project / "app/src/main/java/com/fush/erp/data/FushDatabase.kt"
    gradle = project / "app/build.gradle.kts"
    app_container = project / "app/src/main/java/com/fush/erp/data/AppContainer.kt"
    if not re.search(r"FUSH_DB_SCHEMA_VERSION\s*=\s*35\b", db.read_text()):
        raise RuntimeError("expected Central logical Room schema 35")
    if 'applicationId = "com.fush.erp.recovery"' not in gradle.read_text():
        raise RuntimeError("unexpected Application ID")
    all_main = "\n".join(p.read_text(errors="ignore") for p in (project / "app/src/main").rglob("*.kt"))
    if "fallbackToDestructiveMigration" in all_main:
        raise RuntimeError("destructive fallback detected")
    data_text = "\n".join(p.read_text(errors="ignore") for p in (project / "app/src/main/java/com/fush/erp/data").rglob("*.kt"))
    if not re.search(r"Migration\(34\s*,\s*35\)", data_text):
        raise RuntimeError("Central 34->35 migration not found")
    if "MIGRATION_34_35" not in app_container.read_text():
        raise RuntimeError("Central AppContainer does not register 34->35 migration")


def apply_one(project: Path, path: str, text: str, direct: bool) -> None:
    with tempfile.NamedTemporaryFile("w", suffix=".patch", delete=False) as f:
        f.write(text)
        patch_path = f.name
    try:
        if direct:
            run("git", "apply", "--check", f"--include={path}", patch_path, cwd=project)
            run("git", "apply", f"--include={path}", patch_path, cwd=project)
        else:
            # Exact-context hunk application only; no fuzz and no whole-file replacement.
            run("patch", "--dry-run", "--batch", "--fuzz=0", "-p1", cwd=project, input_text=text)
            run("patch", "--batch", "--fuzz=0", "-p1", cwd=project, input_text=text)
    finally:
        os.unlink(patch_path)


def adapt_current_schema_test(project: Path) -> None:
    p = project / "app/src/androidTest/java/com/fush/erp/backup/DatabaseEncryptionInstrumentedTest.kt"
    s = p.read_text()
    if "PRAGMA user_version = 34" not in s:
        raise RuntimeError("expected old schema-34 fixture marker was not found")
    s = s.replace("PRAGMA user_version = 34", "PRAGMA user_version = 35")
    s = s.replace("assertEquals(34,", "assertEquals(35,")
    p.write_text(s)


def verify_result(project: Path) -> tuple[list[str], bytes]:
    # Make newly-created files visible to git diff without staging content.
    run("git", "add", "-N", ".", cwd=project)
    run("git", "diff", "--check", cwd=project)
    names = [x for x in run("git", "diff", "--name-only", cwd=project).splitlines() if x]
    if set(names) != ALLOWED or len(names) != len(ALLOWED):
        raise RuntimeError(f"unexpected changed-file set: {names}")
    forbidden = [n for n in names if re.search(r"FushDatabase\.kt|Migrations\.kt|SecurityMigrations\.kt|app/schemas/", n)]
    if forbidden:
        raise RuntimeError(f"Room logical schema/migration files changed: {forbidden}")

    verify_baseline(project)
    gradle = (project / "app/build.gradle.kts").read_text()
    if "net.zetetic:sqlcipher-android:4.17.0" not in gradle:
        raise RuntimeError("SQLCipher dependency missing")
    appc = (project / "app/src/main/java/com/fush/erp/data/AppContainer.kt").read_text()
    for marker in ("SupportOpenHelperFactory", "DatabaseEncryptionManager", "openHelperFactory"):
        if marker not in appc:
            raise RuntimeError(f"AppContainer SQLCipher integration missing: {marker}")
    backup = (project / "app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt").read_text()
    for marker in ("DatabaseEncryptionManager", "exportPlaintextCopy"):
        if marker not in backup:
            raise RuntimeError(f"Backup/restore SQLCipher integration missing: {marker}")
    test = (project / "app/src/androidTest/java/com/fush/erp/backup/DatabaseEncryptionInstrumentedTest.kt").read_text()
    if "PRAGMA user_version = 35" not in test:
        raise RuntimeError("instrumented migration test is not schema 35")

    patch = run("git", "diff", "--binary", cwd=project).encode()
    return sorted(names), patch


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo-root", required=True)
    ap.add_argument("--project-root", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    repo_root = Path(args.repo_root).resolve()
    project = Path(args.project_root).resolve()
    out = Path(args.out).resolve(); out.mkdir(parents=True, exist_ok=True)

    verify_baseline(project)
    old_patch = reconstruct_old_patch(repo_root)
    pieces = split_patch(old_patch)
    missing = ALLOWED.difference(pieces)
    if missing:
        raise RuntimeError(f"old functional payload missing expected files: {sorted(missing)}")

    for path in sorted(DIRECT_GIT_APPLY):
        apply_one(project, path, pieces[path], direct=True)
    for path in sorted(SEMANTIC_HUNK_APPLY):
        apply_one(project, path, pieces[path], direct=False)
    adapt_current_schema_test(project)

    names, patch = verify_result(project)
    patch_file = out / "P4_SQLCipher_Central35.patch"
    patch_file.write_bytes(patch)
    (out / "P4_SQLCipher_Central35.patch.sha256").write_text(f"{sha256(patch)}  {patch_file.name}\n")
    (out / "CHANGED_FILES.txt").write_text("\n".join(names) + "\n")
    print(f"P4 Central35 functional rebase OK: {sha256(patch)}")

if __name__ == "__main__":
    main()
