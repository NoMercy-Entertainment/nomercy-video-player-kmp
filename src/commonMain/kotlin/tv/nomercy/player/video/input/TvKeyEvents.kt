// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.input

import tv.nomercy.player.core.events.EventKey
import tv.nomercy.player.core.plugin.pluginEventKey

// What the remote asks the chrome for.
//
// Events rather than calls, because the key handler has to work on a television
// with no chrome mounted at all. A press that nothing is listening for is a
// press that does nothing, which is the correct outcome for a player embedded
// bare, and the alternative is a handler that crashes when a panel it assumed
// was there is not.
//
// Both spellings are given: the bare names the plugin publishes under, and the
// namespaced ones a consumer subscribes to. A listener built from the wrong one
// is silence rather than an error.
public object TvKeyEvents {

    public val Info: EventKey<TvPlaybackSummary> = EventKey("info")

    public val Bookmark: EventKey<TvBookmark> = EventKey("bookmark")

    public val InfoOnPlayer: EventKey<TvPlaybackSummary> =
        pluginEventKey(TvKeyHandlerPlugin.Manifest, "info")

    public val BookmarkOnPlayer: EventKey<TvBookmark> =
        pluginEventKey(TvKeyHandlerPlugin.Manifest, "bookmark")
}
