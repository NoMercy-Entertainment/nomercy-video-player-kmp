// swift-tools-version:5.9
import Foundation
import PackageDescription

// The repository URL entry point. Local Apple tests use the nested manifest.
let local = ProcessInfo.processInfo.environment["NOMERCY_SPM_LOCAL"] == "1"

let engine: Target = local
    ? .binaryTarget(
        name: "NoMercyVideoPlayer",
        path: "build/XCFrameworks/release/NoMercyVideoPlayer.xcframework"
    )
    : .binaryTarget(
        name: "NoMercyVideoPlayer",
        url: "https://github.com/NoMercy-Entertainment/nomercy-video-player-kmp/releases/download/v0.2.30/NoMercyVideoPlayer.xcframework.zip",
        checksum: "2f4c94e72032440a5a914dfc182551fe965ddf3ba88f0dfd7d45620dd1a92fa3"
    )

let package = Package(
    name: "NoMercyVideoPlayer",
    platforms: [.iOS(.v15), .tvOS(.v15)],
    products: [
        .library(name: "NoMercyVideoPlayer", targets: ["NoMercyVideoPlayer"]),
        .library(name: "NoMercyPlayer", targets: ["NoMercyPlayer"]),
    ],
    targets: [
        engine,
        .target(
            name: "NoMercyPlayer",
            dependencies: ["NoMercyVideoPlayer"],
            path: "apple/NoMercyPlayer/Sources/NoMercyPlayer"
        ),
    ]
)
