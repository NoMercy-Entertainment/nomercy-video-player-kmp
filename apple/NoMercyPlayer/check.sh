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
    -scheme NoMercyPlayer \
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

run_tests() {
  local udid
  udid="$(device_id "$2")"
  if [ -z "$udid" ]; then
    echo "no $1 simulator installed — install one from Xcode > Settings > Components" >&2
    exit 1
  fi
  echo "testing on $2 ($udid)"
  xcodebuild test -scheme NoMercyPlayer -destination "id=$udid" -quiet
}

run_tests iOS "iPhone"
run_tests tvOS "Apple TV"

echo "Apple views: build on iOS and tvOS, behaviour gates green on both"
