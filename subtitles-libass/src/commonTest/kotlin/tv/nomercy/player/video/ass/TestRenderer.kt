// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ass

import tv.nomercy.player.video.subtitles.AssRenderer

// A real renderer for this target, or null where there cannot be one.
//
// The subsystem gate is shared source and has to reach the actual binding on
// every platform, but the thing a renderer is built from differs: Android needs
// a Context and nothing else does. Naming that in the gate would put an Android
// import in a file iOS also compiles.
//
// Null means this host genuinely has no libass — a Windows desktop, or an
// Android host test where the native library cannot load at all. It does not
// mean "skip if inconvenient", and the gates that receive it say so.
internal expect fun newTestRenderer(): AssRenderer?
