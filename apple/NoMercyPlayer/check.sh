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
# driving it.
SIMULATOR="$(xcrun simctl list devices available | grep -m1 -oE 'iPhone [0-9]+[^(]*' | sed 's/ *$//')"
echo "testing on $SIMULATOR"
xcodebuild test \
  -scheme NoMercyPlayer \
  -destination "platform=iOS Simulator,name=$SIMULATOR" \
  -quiet

echo "Apple views: build on iOS and tvOS, behaviour gates green"
