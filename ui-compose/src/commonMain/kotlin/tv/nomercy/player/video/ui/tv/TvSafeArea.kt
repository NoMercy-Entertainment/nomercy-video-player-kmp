// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui.tv

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tv.nomercy.player.core.device.SafeAreaInsets

// How far in this screen is safe to draw.
//
// Provided rather than measured here, because the answer comes from the platform
// and needs a context the composition does not carry. A host reads it once and
// provides it; everything below reads it from here.
//
// Empty by default, which is right for every surface that is not a television. A
// phone and a desktop crop nothing, and a default that assumed otherwise would
// put a border around every picture in the product.
public val LocalSafeAreaInsets: ProvidableCompositionLocal<SafeAreaInsets> =
    staticCompositionLocalOf { SafeAreaInsets() }

// Keeps whatever it wraps inside the part of the screen the viewer can see.
//
// The number comes from the contract rather than from a constant here, and that
// is the whole point. The client this replaces had a fixed inset written into
// each widget, so a television that cropped more lost its controls and one that
// cropped less carried a needless border, and neither could be corrected in one
// place.
//
// A composable rather than a plain modifier factory, because reading a
// composition local is only possible during composition. Written inside a
// composable it picks up whatever the host provided, including an override for a
// box that does know its own overscan.
@Composable
public fun Modifier.tvSafeArea(): Modifier = padding(LocalSafeAreaInsets.current.asPadding())

// The padding those insets describe, in the units Compose measures in.
public fun SafeAreaInsets.asPadding(): PaddingValues = PaddingValues(
    start = left.dp,
    top = top.dp,
    end = right.dp,
    bottom = bottom.dp,
)
