#!/usr/bin/env python3
# -----------------------------------------------------------------------------
#  Copyright (c) NoMercy Entertainment
#
#  Licensed under the Apache License, Version 2.0. See LICENSE for details.
#
#  SPDX-License-Identifier: Apache-2.0
# -----------------------------------------------------------------------------

"""The mutation half of the parity coverage gate (P30.9), the hand-rolled way.

No off-the-shelf mutation-testing tool covers this stack today — both were
tried and rejected with real errors, recorded in the P30.9 tasklist entry:
kotlinx-kover ships no mutation testing at all, and info.solidsoft.pitest
applies without crashing but registers zero tasks because it hooks the
classic Java plugin's `sourceSets`, which a pure Kotlin Multiplatform project
never has.

This is the plan's own fallback (_shared-contract.md R5/B3): "mutate the...
core..., require the scenario set to redden. A surviving mutant is a
behaviour nobody asserts." Applied by hand to one real load-bearing file
(ResumeGuard, P15.11) rather than to every source file a general-purpose
mutator would reach — this proves the MECHANISM (a real behavioural change
here is actually caught), not exhaustive mutation coverage of the whole repo.

Usage: python3 scripts/mutation-smoke-test.py
Exit 0 only if every mutant reddened the suite and the revert came back green.
"""

import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
TARGET = REPO_ROOT / "src/commonMain/kotlin/tv/nomercy/player/video/ResumeGuard.kt"
GRADLEW = REPO_ROOT / "gradlew.bat" if sys.platform == "win32" else REPO_ROOT / "gradlew"
TEST_FILTER = "*ResumeGuardTest*"

# Each mutant: a (find, replace) pair that flips one real decision in
# ResumeGuard.startPositionMs. Every one of these must make the test suite
# fail — if it doesn't, the suite is not actually asserting that branch.
MUTANTS = [
    (
        "the near-end comparison (>= -> <)",
        "savedSeconds >= durationSeconds - TRAILING_SECONDS",
        "savedSeconds < durationSeconds - TRAILING_SECONDS",
    ),
    (
        "the percent-threshold comparison (>= -> <)",
        "percentComplete >= PERCENT_THRESHOLD",
        "percentComplete < PERCENT_THRESHOLD",
    ),
    (
        "the guard's OR into AND",
        "if (nearEnd || highPercent) 0L else savedSeconds * 1000L",
        "if (nearEnd && highPercent) 0L else savedSeconds * 1000L",
    ),
]


def run_tests() -> bool:
    # `:jvmTest`, not `jvmTest` — the bare task name also matches `ui-compose`'s
    # own jvmTest, which then fails on "no tests found" for a filter that was
    # only ever meant for this module's ResumeGuardTest.
    result = subprocess.run(
        [str(GRADLEW), ":jvmTest", "--tests", TEST_FILTER, "--rerun-tasks"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    return result.returncode == 0


def main() -> int:
    original = TARGET.read_text(encoding="utf-8")

    print("Baseline (unmutated) run — must be GREEN")
    if not run_tests():
        print("FAIL: the unmutated suite is not green — fix that first, a mutation test proves nothing over a red baseline")
        return 1
    print("  green\n")

    failures = []
    for label, find, replace in MUTANTS:
        if find not in original:
            print(f"FAIL: mutant target text not found for '{label}' — ResumeGuard.kt changed shape, update this script")
            failures.append(label)
            continue

        mutated = original.replace(find, replace, 1)
        TARGET.write_text(mutated, encoding="utf-8")
        try:
            print(f"Mutant: {label} — expecting RED")
            survived = run_tests()
            if survived:
                print(f"  SURVIVED — no test caught this. That is a behaviour nobody asserts.")
                failures.append(label)
            else:
                print("  killed (suite went red, as it should)\n")
        finally:
            TARGET.write_text(original, encoding="utf-8")

    print("Revert — confirming the restore is exact and the suite is GREEN again")
    if not run_tests():
        print("FAIL: the file did not restore cleanly, or something else broke")
        return 1
    print("  green\n")

    if failures:
        print(f"MUTATION SMOKE TEST FAILED — {len(failures)} surviving mutant(s): {', '.join(failures)}")
        return 1

    print(f"MUTATION SMOKE TEST PASSED — all {len(MUTANTS)} planted mutants were killed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
