import json
from pathlib import Path
import tempfile
import unittest
from make_update_manifest import create


class ReleaseManifestTest(unittest.TestCase):
    def test_metadata_matches_actual_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "app.apk"; apk.write_bytes(b"test-apk")
            notes = root / "notes.md"; notes.write_text("更新内容", encoding="utf-8")
            metadata = root / "output-metadata.json"
            data = {"applicationId": "jp.urea.gofilesafeviewer", "minSdkVersionForDexing": 23,
                    "elements": [{"versionName": "0.10.0", "versionCode": 10}]}
            metadata.write_text(json.dumps(data), encoding="utf-8")
            result = create(apk, metadata, notes, root / "update.json")
            self.assertEqual(result["sizeBytes"], 8)
            self.assertEqual(len(result["sha256"]), 64)
            self.assertEqual(result["minSdk"], 23)
            self.assertIn("/v0.10.0/", result["apkUrl"])
            data["applicationId"] += ".debug"
            metadata.write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaises(ValueError): create(apk, metadata, notes, root / "bad.json")


if __name__ == "__main__": unittest.main()
