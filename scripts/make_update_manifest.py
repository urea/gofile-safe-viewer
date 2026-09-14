#!/usr/bin/env python3
"""Create the pinned release metadata from the actual APK and AGP output metadata."""
import argparse
import hashlib
import json
from pathlib import Path
import re


def create(apk: Path, metadata: Path, notes: Path, output: Path) -> dict:
    build = json.loads(metadata.read_text(encoding="utf-8"))
    elements = build["elements"]
    if build["applicationId"] != "jp.urea.gofilesafeviewer" or len(elements) != 1:
        raise ValueError("Expected one release APK for jp.urea.gofilesafeviewer")
    item = elements[0]
    version = item["versionName"]
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version) or not 0 < item["versionCode"] <= 2147483647:
        raise ValueError("Invalid release version")
    content = apk.read_bytes()
    if not 0 < len(content) <= 100 * 1024 * 1024:
        raise ValueError("Invalid APK size")
    release_notes = notes.read_text(encoding="utf-8").strip()
    if len(release_notes) > 8000:
        raise ValueError("Release notes are too long")
    result = {"schemaVersion": 1, "applicationId": build["applicationId"],
              "versionCode": item["versionCode"], "versionName": version,
              "minSdk": int(build["minSdkVersionForDexing"]),
              "sizeBytes": len(content), "sha256": hashlib.sha256(content).hexdigest(),
              "apkUrl": f"https://github.com/urea/gofile-safe-viewer/releases/download/v{version}/GofileSafeViewer.apk",
              "releaseNotes": release_notes}
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--notes", type=Path, default=Path("docs/release-notes.md"))
    parser.add_argument("--output", type=Path, default=Path("update.json"))
    args = parser.parse_args()
    print(create(args.apk, args.metadata, args.notes, args.output)["versionName"])
