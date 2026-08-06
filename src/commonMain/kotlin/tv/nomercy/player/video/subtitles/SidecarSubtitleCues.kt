// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.subtitles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tv.nomercy.player.core.cues.Cue
import tv.nomercy.player.core.cues.TextPayload
import tv.nomercy.player.core.cues.VttSubtitlePayload
import tv.nomercy.player.core.events.ALIGN_CENTER
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.CueEvent
import tv.nomercy.player.core.events.FULL_SIZE
import tv.nomercy.player.core.events.SubtitleCue
import tv.nomercy.player.core.events.SubtitleCueChange
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.core.media.CueCrossing
import tv.nomercy.player.core.media.CueTracker
import tv.nomercy.player.core.plugin.PluginHost
import tv.nomercy.player.core.ports.CueParser
import tv.nomercy.player.core.ports.FetchOptions
import tv.nomercy.player.core.ports.SubtitleTrack

// A subtitle file that is not inside the film, played anyway.
//
// The engine's own tracks are the easy half: it decodes them and reports the
// cues. A .vtt sitting beside the film is nobody's job by default — no engine
// will fetch a file it was not given, so without this the whole sidecar half of
// a NoMercy library is unreachable, which is most of it.
//
// It is the web kit's `_startSidecarSubtitle`, step for step: fetch the file,
// parse it with whatever parser the host resolves for the url, run a CueTracker
// over the playhead, and emit the ACTIVE SET on every crossing. The active set
// rather than the cue that just changed, because that is what the channel
// carries — a renderer reading one event must be able to draw everything on
// screen without remembering the last one.
//
// It emits the same `subtitleCue` event an engine's cues arrive on. That is the
// whole point of the shared channel: a renderer, a Connect client and a
// screenshot test all see one stream, and none of them has to know that this
// film's captions came out of a separate file.
internal class SidecarSubtitleCues(
    private val host: PluginHost,
    private val scope: CoroutineScope,
) {

    // The track whose file is playing, so a caller can answer "which subtitle is
    // on" for a track the engine has never heard of. Core reads its answer off
    // the engine, and the engine has nothing to say about a sidecar.
    var active: SubtitleTrack? = null
        private set

    private var loading: Job? = null
    private var ticking: Subscription? = null
    private var tracker: CueTracker<TextPayload>? = null
    private var language: String? = null

    // Bumped on every selection, so a fetch that lands after the viewer has
    // moved on cannot install its cues over the ones they chose instead. The web
    // guards the same race by checking whether the slot was repopulated while it
    // was waiting.
    private var generation: Long = 0L

    // Whether this selection is ours.
    //
    // Returns false for an engine track so the caller knows to leave the engine
    // alone; a sidecar answers true and the engine is told to show nothing,
    // because two producers feeding one channel is a picture with two sets of
    // captions on it.
    fun select(track: SubtitleTrack?): Boolean {
        stop()
        generation += 1
        active = track?.takeIf { it.url != null }

        val url: String = active?.url ?: run {
            // Off, or an engine track. Either way whatever this was drawing has
            // to come off the picture; the engine announces its own empty change
            // for its own tracks.
            if (track == null) announce(emptyList())
            return false
        }

        language = active?.language
        val epoch: Long = generation
        loading = scope.launch { load(url, epoch) }
        return true
    }

    fun dispose() {
        stop()
        active = null
    }

    private fun stop() {
        loading?.cancel()
        loading = null
        ticking?.dispose()
        ticking = null
        tracker = null
    }

    private suspend fun load(url: String, epoch: Long) {
        val cues: List<Cue<TextPayload>> = parse(url, fetchText(url))
        // The viewer changed their mind while the file was in flight. Installing
        // now would replace the track they actually chose.
        if (epoch != generation) return

        if (cues.isEmpty()) {
            // A file that arrived empty, failed to parse, or could not be
            // fetched. Announced as no cues rather than left silent: a renderer
            // still showing the previous track's line would be worse than a
            // blank picture, and the state is honestly "nothing to draw".
            announce(emptyList())
            return
        }

        val started = CueTracker(cues)
        tracker = started
        ticking = host.on(CoreEvents.Time) { update -> advance(started, update.time) }
    }

    // Only when something crossed. A tracker reports nothing between cues, and
    // emitting on every tick would have every renderer redraw the same line four
    // times a second for the length of a film.
    private fun advance(started: CueTracker<TextPayload>, seconds: Double) {
        if (started !== tracker) return

        val crossing: CueCrossing<TextPayload> = started.advanceTo(seconds)
        if (!crossing.changed) return

        // The crossing itself, on the player's own channel.
        //
        // cue:enter and cue:exit were declared, catalogued and emitted by
        // nothing: the tracker computed both halves and handed them to a caller
        // that announced only the resulting LIST. A consumer wiring
        // `on('cue:enter')` — which is what the reference documents, and how a
        // karaoke overlay or an analytics hook is written — heard nothing ever,
        // while `subtitleCue` fired beside it.
        crossing.entered.forEach { cue -> host.emit(CoreEvents.CueEnter, eventOf(cue)) }
        crossing.exited.forEach { cue -> host.emit(CoreEvents.CueExit, eventOf(cue)) }

        announce(started.active.map { cueOf(it.payload) })
    }

    // Seconds on the wire, as the reference's payload carries them.
    private fun eventOf(cue: Cue<TextPayload>): CueEvent = CueEvent(
        id = cue.id,
        startTime = cue.start,
        endTime = cue.end,
        text = cue.payload.text,
    )

    private fun announce(cues: List<SubtitleCue>) {
        host.emit(CoreEvents.SubtitleCue, SubtitleCueChange(cues = cues, language = language))
    }

    // Null body on any failure, because a subtitle file that will not load is
    // not a reason to stop a film. The web's sidecar path swallows the same way
    // and for the same reason.
    private suspend fun fetchText(url: String): String? = runCatching {
        host.fetch(url, FetchOptions()).body
    }.getOrNull()

    // Through the host's registry rather than a parser named here, so a consumer
    // who registered their own format for a url gets it — the same answer
    // `player.resolveCueParser(url)` gives them.
    private fun parse(url: String, raw: String?): List<Cue<TextPayload>> {
        if (raw.isNullOrBlank()) return emptyList()

        val parser: CueParser<*> = host.resolveCueParser(url, null) ?: return emptyList()
        val parsed: List<Cue<*>> = runCatching { parser.parse(raw, url) }.getOrDefault(emptyList())

        // Every built-in subtitle payload is a TextPayload; a sprite index is
        // not, and a parser resolved for a url that turned out to be one has no
        // line of dialogue to offer. Dropping those is what keeps a thumbnail
        // manifest from being drawn as captions.
        return parsed.mapNotNull { cue ->
            (cue.payload as? TextPayload)?.let { Cue(cue.start, cue.end, it, cue.id) }
        }
    }
}

// A parsed cue, as the channel carries it.
//
// The web's `_toSubtitleCue`, field for field: the positioning rides along when
// the parser read any, and a payload that carries none lands on the values the
// web writes at this exact seam — centred, full width, no line.
private fun cueOf(payload: TextPayload): SubtitleCue {
    val vtt: VttSubtitlePayload? = payload as? VttSubtitlePayload

    return SubtitleCue(
        text = payload.text,
        plainText = payload.text,
        line = vtt?.linePosition?.toDouble(),
        align = vtt?.alignment ?: ALIGN_CENTER,
        size = vtt?.size?.toDouble() ?: FULL_SIZE,
    )
}
