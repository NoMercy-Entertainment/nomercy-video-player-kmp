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
// Equatable so an icon is, which is what lets a test say "the button shows the
// play glyph" rather than "the button shows a string that used to name one".
public enum PathCommand: Equatable, Sendable {
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
/// general SVG parser: `S` and `T` commands are absent because the table has
/// none, and a parser that silently accepted them by
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

            case "a":
                let radii = scanner.point()
                let rotation = scanner.number()
                let largeArc = scanner.number() != 0
                let sweep = scanner.number() != 0
                let end = absolute(scanner.point())
                commands.append(contentsOf: arc(from: current, to: end, radii: radii,
                                                rotation: rotation, largeArc: largeArc, sweep: sweep))
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

    private static func arc(
        from start: CGPoint, to end: CGPoint, radii: CGPoint,
        rotation: CGFloat, largeArc: Bool, sweep: Bool
    ) -> [PathCommand] {
        var rx = abs(Double(radii.x))
        var ry = abs(Double(radii.y))
        if start == end { return [] }
        if rx == 0 || ry == 0 { return [.line(end)] }

        let phi = Double(rotation) * .pi / 180
        let cosine = cos(phi)
        let sine = sin(phi)
        let dx = (Double(start.x) - Double(end.x)) / 2
        let dy = (Double(start.y) - Double(end.y)) / 2
        let x1 = cosine * dx + sine * dy
        let y1 = -sine * dx + cosine * dy
        let size = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
        if size > 1 {
            let scale = sqrt(size)
            rx *= scale
            ry *= scale
        }

        let numerator = max(0, rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1)
        let denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
        let factor = (largeArc == sweep ? -1.0 : 1.0) * sqrt(numerator / denominator)
        let centerX1 = factor * rx * y1 / ry
        let centerY1 = -factor * ry * x1 / rx
        let centerX = cosine * centerX1 - sine * centerY1 + (Double(start.x) + Double(end.x)) / 2
        let centerY = sine * centerX1 + cosine * centerY1 + (Double(start.y) + Double(end.y)) / 2

        let ux = (x1 - centerX1) / rx
        let uy = (y1 - centerY1) / ry
        let vx = (-x1 - centerX1) / rx
        let vy = (-y1 - centerY1) / ry
        var angle = atan2(uy, ux)
        var extent = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        if sweep && extent < 0 { extent += 2 * .pi }
        if !sweep && extent > 0 { extent -= 2 * .pi }
        let count = max(1, Int(ceil(abs(extent) / (.pi / 2))))
        let step = extent / Double(count)

        func point(_ x: Double, _ y: Double) -> CGPoint {
            CGPoint(x: CGFloat(centerX + rx * cosine * x - ry * sine * y),
                    y: CGFloat(centerY + rx * sine * x + ry * cosine * y))
        }

        var result: [PathCommand] = []
        for index in 0..<count {
            let next = angle + step
            let tangent = 4.0 / 3.0 * tan(step / 4.0)
            let control1 = point(cos(angle) - tangent * sin(angle), sin(angle) + tangent * cos(angle))
            let control2 = point(cos(next) + tangent * sin(next), sin(next) - tangent * cos(next))
            result.append(.curve(to: index == count - 1 ? end : point(cos(next), sin(next)),
                                 control1: control1, control2: control2))
            angle = next
        }
        return result
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
