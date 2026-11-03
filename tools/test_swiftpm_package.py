import pathlib
import re
import subprocess
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
NESTED = ROOT / "apple" / "NoMercyPlayer" / "Package.swift"
ROOT_MANIFEST = ROOT / "Package.swift"


class SwiftPackageTest(unittest.TestCase):
    def test_repository_url_has_a_release_package(self):
        self.assertTrue(ROOT_MANIFEST.is_file(), "SwiftPM needs Package.swift at repository root")
        nested = NESTED.read_text(encoding="utf-8")
        root = ROOT_MANIFEST.read_text(encoding="utf-8")
        self.assertIn('environment["NOMERCY_SPM_LOCAL"] == "1"', nested)
        self.assertIn('environment["NOMERCY_SPM_LOCAL"] == "1"', root)
        self.assertRegex(nested, r'let engine: Target = local\s*\? \.binaryTarget\([\s\S]*?path:')
        self.assertRegex(root, r'let engine: Target = local\s*\? \.binaryTarget\([\s\S]*?path:')
        for field in (r'/download/v[^/]+/NoMercyVideoPlayer\.xcframework\.zip', r'checksum: "[0-9a-f]{64}"'):
            self.assertEqual(re.findall(field, nested), re.findall(field, root))
        self.assertIn('path: "apple/NoMercyPlayer/Sources/NoMercyPlayer"', root)

    def test_release_stamps_both_manifest_entry_points(self):
        script = (ROOT / "tools" / "release-xcframework.sh").read_text(encoding="utf-8")
        stamp = re.search(r"python3 - .*? <<'PY'\n(.*?)\nPY", script, re.S)
        self.assertIsNotNone(stamp)
        fixture_root = ROOT / "build" / "swiftpm-package-test"
        nested = fixture_root / "apple" / "NoMercyPlayer" / "Package.swift"
        root = fixture_root / "Package.swift"
        nested.parent.mkdir(parents=True, exist_ok=True)
        original = 'url: "https://example.test/download/v1/engine.zip",\nchecksum: "' + "a" * 64 + '"\n'
        nested.write_text(original, encoding="utf-8")
        root.write_text(original, encoding="utf-8")
        try:
            subprocess.run(
                [sys.executable, "-c", stamp.group(1), str(nested), "b" * 64, "v2"],
                check=True,
                capture_output=True,
                text=True,
            )
            self.assertEqual(nested.read_text(encoding="utf-8"), root.read_text(encoding="utf-8"))
            self.assertIn('checksum: "' + "b" * 64 + '"', root.read_text(encoding="utf-8"))
        finally:
            nested.unlink(missing_ok=True)
            root.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
