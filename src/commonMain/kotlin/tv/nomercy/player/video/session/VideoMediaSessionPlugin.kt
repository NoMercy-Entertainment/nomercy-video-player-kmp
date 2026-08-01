// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.session

import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.plugin.MediaSessionPlugin
import tv.nomercy.player.core.plugin.TransportCommands
import tv.nomercy.player.core.ports.NowPlaying
import tv.nomercy.player.core.ports.SystemTransport
import tv.nomercy.player.core.ports.defaultSystemTransport
import tv.nomercy.player.video.item.VideoPlaylistItem

// What a television episode looks like on a lock screen.
//
// The core plugin knows an item has a title and nothing else, deliberately: a
// core that invented an artist field would be guessing at every consumer's
// model. This is the library that does have the fields, and the mapping is the
// web player's own — the show becomes the artist line and the season becomes
// the album line, which is how every music-shaped Now Playing surface on these
// platforms lays out three lines of text for something that is not music.
//
// Without it a series episode reaches the lock screen as a bare title with no
// art, because NowPlaying's artist, album and artwork had nothing filling them.
public open class VideoMediaSessionPlugin(
    commands: TransportCommands,
    openTransport: () -> SystemTransport = ::defaultSystemTransport,

    // The season label is the one string this plugin renders, and it is a
    // sentence rather than a number: "Season 3" in English, "Seizoen 3" in
    // Dutch. Same table shape the chrome and the remote handler use, generated
    // from the same web i18n folder.
    private val locale: String = MediaSessionTranslations.FALLBACK,
) : MediaSessionPlugin(commands, openTransport) {

    override fun nowPlayingFor(item: PlaylistItem): NowPlaying {
        val base: NowPlaying = super.nowPlayingFor(item)
        val episode: VideoPlaylistItem = item as? VideoPlaylistItem ?: return base

        return base.copy(
            artist = episode.show,
            album = seasonLabel(episode.season),
            artworkUrl = artworkFor(episode),
        )
    }

    // Three field names for one picture, read in the order the web item
    // documents. A host whose backend calls it a poster and a host whose
    // backend calls it a thumbnail both reach the lock screen.
    private fun artworkFor(item: VideoPlaylistItem): String? =
        item.image ?: item.poster ?: item.thumbnail

    // Null rather than an empty string when there is no season, because these
    // platforms treat an absent field and a blank one differently: an empty
    // album line is a blank row drawn under the title.
    private fun seasonLabel(season: Int?): String? = season?.let { number ->
        MediaSessionTranslations.get(locale, SEASON_KEY).replace(SEASON_TOKEN, number.toString())
    }

    private companion object {
        const val SEASON_KEY = "plugin.media-session.season"

        // The web string interpolates {season}; the table carries it verbatim
        // so the substitution happens here rather than in the generator.
        const val SEASON_TOKEN = "{season}"
    }
}
