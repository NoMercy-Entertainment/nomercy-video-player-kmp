// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

import CoreGraphics
import SwiftUI

/// An SVG path command, already parsed.
///
/// Parsed once at construction rather than on every draw: `path(in:)` is called
/// on every layout pass, and re-tokenising a four-hundred-character string each
/// time is work a scrolling menu pays for repeatedly.
public enum PathCommand: Sendable {
    case move(CGPoint)
    case line(CGPoint)
    case curve(to: CGPoint, control1: CGPoint, control2: CGPoint)
    case quad(to: CGPoint, control: CGPoint)
    case close
}

/// The subset of SVG path syntax the Fluent table actually uses.
///
/// Compose gets this for free — `PathParser` ships with it — and SwiftUI does
/// not, which is the only reason this file exists. It is deliberately not a
/// general SVG parser: arcs, `S`, `T` and elliptical commands are absent
/// because the table has none, and a parser that silently accepted them by
/// ignoring them would draw a subtly wrong icon rather than fail.
///
/// Both absolute and relative forms are handled, because the table uses both.
public enum SVGPathParser {

    public static func parse(_ input: String) -> [PathCommand] {
        var scanner = Scanner(input)
        var commands: [PathCommand] = []
        var current = CGPoint.zero
        var start = CGPoint.zero
        var previous: Character = " "

        while let token = scanner.nextCommand(after: previous) {
            previous = token
            let relative = token.isLowercase
            let absolute = { (point: CGPoint) -> CGPoint in
                relative ? CGPoint(x: current.x + point.x, y: current.y + point.y) : point
            }

            switch Character(token.lowercased()) {
            case "m":
                let point = absolute(scanner.point())
                commands.append(.move(point))
                current = point
                start = point

            case "l":
                let point = absolute(scanner.point())
                commands.append(.line(point))
                current = point

            case "h":
                let x = scanner.number()
                let point = CGPoint(x: relative ? current.x + x : x, y: current.y)
                commands.append(.line(point))
                current = point

            case "v":
                let y = scanner.number()
                let point = CGPoint(x: current.x, y: relative ? current.y + y : y)
                commands.append(.line(point))
                current = point

            case "c":
                let control1 = absolute(scanner.point())
                let control2 = absolute(scanner.point())
                let end = absolute(scanner.point())
                commands.append(.curve(to: end, control1: control1, control2: control2))
                current = end

            case "q":
                let control = absolute(scanner.point())
                let end = absolute(scanner.point())
                commands.append(.quad(to: end, control: control))
                current = end

            case "z":
                commands.append(.close)
                current = start

            default:
                // An unknown command means the table gained syntax this parser
                // does not draw. Stopping is the honest answer: continuing
                // renders an icon missing a stroke, which reads as a design
                // choice rather than as a bug.
                return commands
            }
        }

        return commands
    }

    public static func apply(_ commands: [PathCommand], to path: inout Path) {
        for command in commands {
            switch command {
            case .move(let point): path.move(to: point)
            case .line(let point): path.addLine(to: point)
            case .curve(let to, let c1, let c2): path.addCurve(to: to, control1: c1, control2: c2)
            case .quad(let to, let control): path.addQuadCurve(to: to, control: control)
            case .close: path.closeSubpath()
            }
        }
    }
}

/// A cursor over the path string.
///
/// SVG allows a command letter to be omitted when it repeats, so `L 1 2 3 4` is
/// two line commands. `nextCommand(after:)` returns the previous letter when
/// the next token is a number, which is what implements that.
private struct Scanner {
    private let characters: [Character]
    private var index: Int = 0

    init(_ input: String) {
        characters = Array(input)
    }

    mutating func nextCommand(after previous: Character) -> Character? {
        skipSeparators()
        guard index < characters.count else { return nil }

        let character = characters[index]
        if character.isLetter {
            index += 1
            return character
        }

        // A number where a command was expected: the previous command repeats.
        // After a moveto, an implicit repeat is a lineto, which is the one
        // special case in the syntax and the one people forget.
        guard previous != " " else { return nil }
        if previous == "m" { return "l" }
        if previous == "M" { return "L" }
        return previous
    }

    mutating func point() -> CGPoint {
        let x = number()
        let y = number()
        return CGPoint(x: x, y: y)
    }

    mutating func number() -> CGFloat {
        skipSeparators()
        var digits = ""

        if index < characters.count, characters[index] == "-" || characters[index] == "+" {
            digits.append(characters[index])
            index += 1
        }

        while index < characters.count {
            let character = characters[index]
            if character.isNumber || character == "." {
                digits.append(character)
                index += 1
                continue
            }
            // Exponent form appears in generated path data.
            if character == "e" || character == "E" {
                digits.append(character)
                index += 1
                if index < characters.count, characters[index] == "-" || characters[index] == "+" {
                    digits.append(characters[index])
                    index += 1
                }
                continue
            }
            break
        }

        return CGFloat(Double(digits) ?? 0)
    }

    private mutating func skipSeparators() {
        while index < characters.count {
            let character = characters[index]
            guard character == " " || character == "," || character.isNewline else { break }
            index += 1
        }
    }
}
