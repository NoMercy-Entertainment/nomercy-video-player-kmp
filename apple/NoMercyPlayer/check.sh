#!/usr/bin/env bash
# Build and test the SwiftUI half of the drop-in view, on iOS and on tvOS.
#
# macOS only, and it says so rather than failing obscurely elsewhere. Run from
# the module root:
#
#   ./gradlew assembleNoMercyVideoPlayerXCFramework
#   ./apple/NoMercyPlayer/check.sh
set -euo pipefail

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "xcodebuild not found — the Apple views build on macOS" >&2
  exit 0
fi

FRAMEWORK="build/XCFrameworks/release/NoMercyVideoPlayer.xcframework"
if [ ! -d "$FRAMEWORK" ]; then
  echo "no xcframework — run ./gradlew assembleNoMercyVideoPlayerXCFramework first" >&2
  exit 1
fi

cd apple/NoMercyPlayer

# Both platforms build, because tvOS is the one with no app to fall back on and
# a view that only ever compiled for iOS would be a surprise on the day someone
# needs it.
for destination in "generic/platform=iOS" "generic/platform=tvOS"; do
  echo "building for $destination"
  xcodebuild build \
    -scheme NoMercyVideoPlayer-Package \
    -destination "$destination" \
    -quiet
done

# The behaviour gates run on a simulator, because a binding is only proven by
# driving it — and on both simulators, because the tvOS view is the one with no
# app to fall back on. A gate that only ran on iPhone would leave the greenfield
# surface covered by nothing but a compiler.
# By id, not by name. Simulator names carry their generation in brackets —
# "Apple TV 4K (3rd generation)" — and matching the readable part of that gives
# a name no device has, which xcodebuild reports by listing every destination it
# does know about, visionOS included. The id is unambiguous.
device_id() {
  xcrun simctl list devices available | grep -m1 "$1" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}'
}

# The -Package scheme, not the product one. SPM generates a scheme per product
# plus one for the package, and only the package scheme carries the test target:
# a product scheme reports "not currently configured for the test action", which
# reads like a broken checkout rather than the wrong scheme name.
run_tests() {
  local udid
  udid="$(device_id "$2")"
  if [ -z "$udid" ]; then
    echo "no $1 simulator installed — install one from Xcode > Settings > Components" >&2
    exit 1
  fi
  echo "testing on $2 ($udid)"

  # Not -quiet, and the count is checked.
  #
  # A run that executes NOTHING exits zero. `xcodebuild test` on a scheme whose
  # buildables have no supported platforms prints "Supported platforms for the
  # buildables in the current scheme is empty", finishes in about a second, and
  # succeeds — so this printed "behaviour gates green on both" having run no
  # assertions at all. Proved by planting a failing one and watching it pass.
  #
  # -quiet was hiding the only line that could have shown it. The count is the
  # check now: green with zero tests is the same failure as red, and it is the
  # one that looks like success.
  local out
  out="$(xcodebuild test -scheme NoMercyVideoPlayer-Package -destination "id=$udid" 2>&1)" || {
    printf '%s
' "$out" | grep -E "error:|failed|XCTAssert" | head -20 >&2
    exit 1
  }

  local ran
  ran="$(printf '%s' "$out" | grep -oE "Executed [0-9]+ test" | grep -oE "[0-9]+" | sort -rn | head -1)"

  if [ -z "$ran" ] || [ "$ran" -eq 0 ]; then
    echo "$1: xcodebuild reported no executed tests — the suite did not run" >&2
    printf '%s
' "$out" | grep -E "Supported platforms|Testing started|error:" | head -5 >&2
    exit 1
  fi

  echo "$1: $ran test(s)"
}

run_tests iOS "iPhone"
run_tests tvOS "Apple TV"

echo "Apple views: build on iOS and tvOS, behaviour gates green on both"
