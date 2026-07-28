// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import Foundation
import XCTest
import NoMercyVideoPlayer
import NoMercyPlayerTesting

// The Apple build of the player, driven through the web harness's own scenarios.
//
// The surface gate says the framework declares the right names. This says the
// compiled Apple binary emits them in the right order — which is a different
// question, and the one a viewer notices. Kotlin/Native compiles separately from
// the JVM: same source, different backend, and an ordering that holds on one is
// not proof about the other.
//
// It drives the same fake the Kotlin suites use, from the published testing
// artifact. A stand-in written in Swift would be a second answer to what the
// engine does, and the two would drift without either being wrong on its own.
final class BehavioralConformanceTests: XCTestCase {

    private struct ScenarioFile: Decodable {
        struct Action: Decodable {
            let method: String?
            let preventVia: String?
            let backend: String?
        }
        struct Scenario: Decodable {
            let id: String
            let medium: String
            let actions: [Action]
            let expect: [String]
        }
        let contractVersion: String
        let scenarios: [Scenario]
    }

    private func file() throws -> ScenarioFile {
        let url = try XCTUnwrap(
            Bundle.module.url(forResource: "scenarios", withExtension: "json"),
            "the scenarios are not in the test bundle — check Package.swift resources"
        )
        return try JSONDecoder().decode(ScenarioFile.self, from: Data(contentsOf: url))
    }

    private func relevant() throws -> [ScenarioFile.Scenario] {
        try file().scenarios.filter { $0.medium == "both" || $0.medium == "video" }
    }

    func testTheApplePlayerRunsTheScenariosAtAll() throws {
        // A filter that matched nothing would pass forever, and the whole suite
        // would be a green tick over a player nobody drove.
        XCTAssertGreaterThanOrEqual(try relevant().count, 10)
    }

    func testTheScenariosAndTheFrameworkAgreeOnTheContract() throws {
        XCTAssertEqual(try file().contractVersion, NoMercyVideoPlayer.ContractSurfaceExport.shared.contractVersion)
    }

    func testTheSharedFakeReachedSwift() {
        // The reason this file can exist. Without the testing framework the only
        // way to drive a player here is a second fake, and two stand-ins for one
        // interface are two answers to what the engine does.
        let backend = FakeMediaBackend()

        backend.currentTime(seconds: 42)

        XCTAssertEqual(backend.seekedTo.compactMap { ($0 as? NSNumber)?.doubleValue }, [42])
    }

    func testTheFakeConfirmsPlayOnItsEventStreamHereToo() async throws {
        // The same assertion the Kotlin suite makes about the same object. If
        // the Native build of the fake behaved differently, every scenario run
        // on this platform would be measuring something else.
        let backend = FakeMediaBackend()
        var seen: [String] = []
        backend.on(event: "play") { _ in seen.append("play") }
        backend.on(event: "playing") { _ in seen.append("playing") }

        try await backend.play()

        XCTAssertEqual(seen, ["play", "playing"])
    }
}
