// swift-tools-version:5.9
import Foundation
import PackageDescription

// Two Apple tiers, one package.
//
// NoMercyVideoPlayer is the headless engine — an application that already draws
// its own chrome takes that and nothing else. NoMercyPlayer is the drop-in: the
// SwiftUI player, controls and all, over the same engine. Compose covers Android
// and the desktop; Apple gets SwiftUI, because an app already drawing its chrome
// in SwiftUI does not want a second toolkit in the process.
//
// The engine framework statically links core, so an application importing this
// gets the core symbols with it. There is deliberately no second binaryTarget
// for core here: two copies of the same symbols in one process is a linker error
// nobody can read.
//
// Two shapes of the engine target, chosen by an environment variable. A released
// consumer resolves a checksummed zip from the tag; somebody working in this
// repo has no tag and needs the framework they just built. The default is the
// local path because that is who runs this manifest most — check.sh assembles
// the framework first, and a release is a job that sets the variable.
let releasing = ProcessInfo.processInfo.environment["NOMERCY_SPM_RELEASE"] == "1"

let engine: Target = releasing
    ? .binaryTarget(
        name: "NoMercyVideoPlayer",
        url: "https://github.com/NoMercy-Entertainment/nomercy-video-player-kmp/releases/download/v2.0.0-rc.1/NoMercyVideoPlayer.xcframework.zip",
        // Filled by the release job from tools/package-xcframework.sh. Without a
        // real one a binaryTarget resolves whatever the URL serves, which is a
        // build that changes under a consumer who changed nothing.
        checksum: "REPLACED_BY_RELEASE_JOB"
    )
    : .binaryTarget(
        name: "NoMercyVideoPlayer",
        path: "../../build/XCFrameworks/release/NoMercyVideoPlayer.xcframework"
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
        .target(name: "NoMercyPlayer", dependencies: ["NoMercyVideoPlayer"]),
        // The stand-ins, for the test target only.
        //
        // Its own binary rather than exported into the engine's framework: a
        // consumer shipping a player must not carry test doubles into their
        // production binary, which is the whole reason the fakes are a separate
        // artifact on the Maven side too.
        .binaryTarget(
            name: "NoMercyPlayerTesting",
            path: "../../../nomercy-player-core-kmp/testing/build/XCFrameworks/release/NoMercyPlayerTesting.xcframework"
        ),
        .testTarget(
            name: "NoMercyPlayerTests",
            dependencies: ["NoMercyPlayer", "NoMercyPlayerTesting"],
            // The contract, carried into the test bundle rather than read from
            // a path. A Swift test has no working directory it can rely on —
            // xcodebuild runs it from wherever it likes — and the Kotlin gates
            // read the same bytes from the repository root.
            resources: [
                .copy("conformance/Resources/contract.json"),
                .copy("conformance/Resources/scenarios.json"),
                .copy("conformance/Resources/native-only-errors.json"),
            ]
        ),
    ]
)
