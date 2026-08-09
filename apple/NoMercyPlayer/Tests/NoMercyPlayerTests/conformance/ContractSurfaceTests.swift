// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import XCTest
import NoMercyVideoPlayer

// What the framework declares, against what the ecosystem documents.
//
// The JVM gate asks the classes by reflection and Kotlin/Native has none, so the
// framework hands its catalogues over as values and this compares them to the
// same vendored contract the Kotlin side reads. Without it the Apple half of
// this library is checked by nothing at all: every Swift view compiles against
// whatever the framework happens to export, and a missing event is a binding
// that silently never fires.
final class ContractSurfaceTests: XCTestCase {

    private struct Contract: Decodable {
        struct Event: Decodable {
            let name: String
            let map: String
        }
        let version: String
        let events: [Event]
        let errors: [String]
    }

    // Codes a native stack raises and a browser has no counterpart for, waived
    // by name. The Kotlin gate in core keeps the same list for the same reason;
    // this is its Apple half rather than a second opinion, and the file says so.
    private struct NativeOnly: Decodable {
        let codes: Set<String>
    }

    private func nativeOnly() throws -> NativeOnly {
        let url = try XCTUnwrap(
            Bundle.module.url(forResource: "native-only-errors", withExtension: "json"),
            "the native-only waiver is not in the test bundle — check Package.swift resources"
        )
        return try JSONDecoder().decode(NativeOnly.self, from: Data(contentsOf: url))
    }

    private func contract() throws -> Contract {
        let url = try XCTUnwrap(
            Bundle.module.url(forResource: "contract", withExtension: "json"),
            "the contract is not in the test bundle — check Package.swift resources"
        )
        return try JSONDecoder().decode(Contract.self, from: Data(contentsOf: url))
    }

    func testTheFrameworkAndTheFixtureDescribeTheSameContract() throws {
        // Two files that drifted apart independently would each look right.
        XCTAssertEqual(ContractSurfaceExport.shared.contractVersion, try contract().version)
    }

    func testEveryDocumentedEventIsDeclaredByTheFramework() throws {
        // Base events only, taken from the contract's own tag. The contract
        // carries the video and music maps too, and core declaring one of those
        // would be core doing a library's job.
        //
        // Tagged by the fixture rather than by a list here: the first draft
        // filtered against the framework's own names, which made this assertion
        // "everything declared is declared" and true no matter what was missing.
        let documented = Set(try contract().events.filter { $0.map == "base" }.map(\.name))
        let declared = Set(ContractSurfaceExport.shared.eventNames)

        let missing = documented.subtracting(declared)

        XCTAssertTrue(missing.isEmpty, "declared by nothing in the framework: \(missing.sorted())")
    }

    func testTheFrameworkInventsNoEventTheEcosystemDoesNotKnow() throws {
        // The direction that matters more. A consumer subscribing to an event
        // no other client emits has written code that will never run, and the
        // compiler is happy either way.
        let documented = Set(try contract().events.map(\.name))
        let stray = Set(ContractSurfaceExport.shared.eventNames).subtracting(documented)

        XCTAssertTrue(stray.isEmpty, "the framework declares events the contract does not: \(stray.sorted())")
    }

    func testTheFrameworkInventsNoErrorCode() throws {
        let documented = Set(try contract().errors)
        let stray = Set(ContractSurfaceExport.shared.errorCodes)
            .subtracting(documented)
            .subtracting(try nativeOnly().codes)

        XCTAssertTrue(stray.isEmpty, "error codes no other client recognises: \(stray.sorted())")
    }

    func testEveryWaivedCodeIsStillOneTheFrameworkRaises() throws {
        // The waiver is the dangerous half of the gate above: a list that grows
        // quietly turns "invents nothing" into "invents whatever is listed". A
        // code the framework no longer declares has no business being excused,
        // and a waiver nobody deletes is how the next invention gets in.
        let declared = Set(ContractSurfaceExport.shared.errorCodes)
        let orphaned = try nativeOnly().codes.subtracting(declared)

        XCTAssertTrue(orphaned.isEmpty, "waived and then never raised — delete these: \(orphaned.sorted())")
    }

    func testTheCatalogueIsActuallyPopulated() {
        // A comparison against an empty set passes forever. This is the
        // assertion that says the export reached Swift at all.
        XCTAssertGreaterThan(ContractSurfaceExport.shared.eventNames.count, 50)
        XCTAssertGreaterThan(ContractSurfaceExport.shared.errorCodes.count, 20)
    }
}
