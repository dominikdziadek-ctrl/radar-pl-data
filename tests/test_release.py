import datetime as dt
import gzip
import hashlib
import json
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_release import validate_change, validate_limits
from canard_source import decompress_base64, normalize


class ReleaseTests(unittest.TestCase):
    # LZString.compressToBase64('Hello') from the reference implementation.
    def test_lz_string_compatibility(self):
        self.assertEqual(decompress_base64("BIUwNmD2Q==="), "Hello")

    def test_missing_layer_rejected(self):
        with self.assertRaises(ValueError):
            normalize("<html></html>")

    def test_suspicious_drop_rejected(self):
        old = {"cameras": [{}] * 200}
        with self.assertRaises(ValueError):
            validate_change({"cameras": [{}] * 149}, old)

    def test_bad_limit_rejected(self):
        pack = {"schema": 1, "profile": "motorcar-no-trailer", "entries": [{"id": "x", "forward": 160, "backward": 0}]}
        with self.assertRaises(ValueError):
            validate_limits(pack)

    def test_published_hashes_and_gzip(self):
        manifest_path = ROOT / "public/manifest.json"
        if not manifest_path.exists():
            self.skipTest("release has not been built")
        manifest = json.loads(manifest_path.read_text())
        for key in ("canard", "limits"):
            data = (ROOT / "public" / manifest[key]["file"]).read_bytes()
            self.assertEqual(hashlib.sha256(data).hexdigest(), manifest[key]["sha256"])
            self.assertTrue(json.loads(gzip.decompress(data)))


if __name__ == "__main__":
    unittest.main()
