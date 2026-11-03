import pathlib
import re
import subprocess
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "release-xcframework.sh"
BUILD = ROOT / "build" / "release-manifest-test"


class ReleaseManifestTest(unittest.TestCase):
    def test_stamps_new_checksum_over_old_hex_value(self):
        script = SCRIPT.read_text(encoding="utf-8")
        stamp = re.search(r"python3 - .*? <<'PY'\n(.*?)\nPY", script, re.S)
        self.assertIsNotNone(stamp, "release script has no manifest stamping step")
        BUILD.mkdir(parents=True, exist_ok=True)
        manifest = BUILD / "Package.swift"
        old = "a" * 64
        new = "b" * 64
        manifest.write_text(
            'url: "https://example.test/download/v1/engine.zip",\n'
            f'checksum: "{old}"\n',
            encoding="utf-8",
        )
        try:
            subprocess.run(
                [sys.executable, "-c", stamp.group(1), str(manifest), new, "v2"],
                check=True,
                capture_output=True,
                text=True,
            )
            result = manifest.read_text(encoding="utf-8")
            self.assertIn(f'checksum: "{new}"', result)
            self.assertIn("/download/v2/", result)
        finally:
            manifest.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
