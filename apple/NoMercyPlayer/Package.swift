// swift-tools-version:5.9
import PackageDescription

// The SwiftUI half of the drop-in view.
//
// Compose covers Android and the desktop; Apple gets SwiftUI, because an iOS or
// tvOS app that already draws its chrome in SwiftUI does not want a second
// toolkit in the process, and a Compose surface on iOS fights the app it would
// be embedded in.
//
// The framework is a build artifact rather than a checked-in binary, so this
// package only resolves after ./gradlew assembleNoMercyVideoPlayerXCFramework.
// check.sh does that first.
let package = Package(
    name: "NoMercyPlayer",
    platforms: [.iOS(.v15), .tvOS(.v15)],
    products: [
        .library(name: "NoMercyPlayer", targets: ["NoMercyPlayer"]),
    ],
    targets: [
        .binaryTarget(
            name: "NoMercyVideoPlayer",
            path: "../../build/XCFrameworks/release/NoMercyVideoPlayer.xcframework"
        ),
        .target(name: "NoMercyPlayer", dependencies: ["NoMercyVideoPlayer"]),
        .testTarget(name: "NoMercyPlayerTests", dependencies: ["NoMercyPlayer"]),
    ]
)
