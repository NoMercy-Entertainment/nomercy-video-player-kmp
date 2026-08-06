// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.nomercy.player.core.controllers.ComposedPlayer
import tv.nomercy.player.core.cues.ChapterCues
import tv.nomercy.player.core.errors.CoreErrorCodes
import tv.nomercy.player.core.errors.stateError
import tv.nomercy.player.core.events.CoreEvents
import tv.nomercy.player.core.events.SubtitleStyle
import tv.nomercy.player.core.ports.CastSender
import tv.nomercy.player.core.events.SubtitlesPayload
import tv.nomercy.player.core.events.Subscription
import tv.nomercy.player.core.media.Chapter
import tv.nomercy.player.core.media.PlaylistItem
import tv.nomercy.player.core.media.normalizeLanguage
import tv.nomercy.player.core.player.ActionOptions
import tv.nomercy.player.core.player.AudioTrackState
import tv.nomercy.player.core.player.PlayerConfig
import tv.nomercy.player.core.ports.AudioTrack
import tv.nomercy.player.core.ports.FetchOptions
import tv.nomercy.player.core.ports.Fetcher
import tv.nomercy.player.core.ports.SubtitleTrack
import tv.nomercy.player.core.ports.MediaBackend
import tv.nomercy.player.core.media.DynamicRange
import tv.nomercy.player.core.media.QualityDescriptor
import tv.nomercy.player.core.ports.QualityLevel
import tv.nomercy.player.core.ports.Storage
import tv.nomercy.player.core.ports.VideoBackend
import tv.nomercy.player.video.item.VideoPlaylistItem
import tv.nomercy.player.video.subtitles.SidecarSubtitleCues

// The index of the track whose language tag answers to [target], or null.
//
// Exact match wins outright. Failing that, the FIRST prefix match in list order
// counts in either direction — "en" answers to "en-US" and "en-GB" answers to
// "en" — and the fallback order is the part that gets guessed wrong: an exact
// match found later in the list still beats a prefix match found earlier.
internal fun matchLanguage(candidates: List<String?>, target: String): Int? {
    val wanted: String = target.lowercase()
    var prefixMatch: Int? = null

    for ((index, candidate) in candidates.withIndex()) {
        val language: String = candidate?.lowercase().orEmpty()
        if (language.isEmpty()) continue
        if (language == wanted) return index
        if (prefixMatch == null && sharesPrefix(language, wanted)) prefixMatch = index
    }
    return prefixMatch
}

// Either direction, because a viewer's "en" should find "en-US" and a viewer's
// "en-GB" should fall back to a plain "en".
private fun sharesPrefix(language: String, wanted: String): Boolean =
    language.startsWith("$wanted-") || wanted.startsWith("$language-")

// How two tracks from different sources are told apart, the reference's
// `_subtitleTrackKey`.
//
// Deliberately not [matchLanguage], which is how a track is CHOSEN and answers
// on a prefix walk over a list. Displacement is a different question and the
// reference answers it with an equality on a canonical form, which is what
// closes the gap: "ger" and "de" share no prefix in either direction, so a
// container that wrote the bibliographic code kept its track and the viewer got
// two rows of German. Normalised, they are one language.
//
// The primary subtag is all that survives, so an "en-GB" file displaces a plain
// "en" track. That is the reference's call, not a simplification of it.
//
// Language alone, where the reference keys on language and kind. Its
// SubtitleTrack separates subtitles from captions and this one does not, so
// there is no second half to key on, and adding a field to carry it would be
// inventing a distinction no engine here reports.
private fun subtitleTrackKey(track: SubtitleTrack): String =
    normalizeLanguage(track.language).orEmpty()

// A video player.
//
// It is a core player plus six things only video has: fullscreen,
// picture-in-picture, theater, how the picture is fitted, where it ended up on
// screen, and skipping a named segment. Everything else — play, queue, volume,
// plugins, state — comes from the core composition and is not restated here.
//
// The view-mode methods only record and announce the state. A player cannot put
// itself fullscreen: that belongs to the Activity, the window or the browser,
// and a library that tried would be fighting whichever one it guessed wrong
// about. A chrome listens for the event and does it, then the state here agrees
// with what the viewer sees.
// Six domain concepts, each with a getter, a setter and mostly a toggle. The
// width is the surface a video chrome needs, and splitting it into a
// ViewModeController the player then forwards to would add a layer whose only
// job is to be passed through.
@Suppress("TooManyFunctions")
public open class NMVideoPlayer(
    private val backend: MediaBackend? = null,
    // The same engine again when it can report tracks and a quality ladder.
    //
    // Passed rather than cast. Core takes these as two parameters precisely so
    // nothing has to ask a MediaBackend whether it is really a VideoBackend, and
    // a video player that skipped this hands core an engine it cannot ask for a
    // subtitle — which is exactly what happened here: the whole track surface
    // answered empty and every menu built from it was blank.
    video: VideoBackend? = null,
    // Where this player's own suspending work runs — a segment window seeking
    // back on a loop, a hold pausing at the boundary. Injectable for the same
    // reason core's is: a test that cannot control the scheduler is asserting
    // against whichever thread happened to win.
    scope: CoroutineScope? = null,
    // How the player reads a file that is not the media itself.
    //
    // A subtitle sitting beside a film is bytes somebody has to go and get, and
    // core deliberately owns no HTTP stack — on a real install the sidecar is
    // behind the same bearer token as the stream and the application already has
    // something that carries it. This was declared on core and had no way
    // through: the video player never took one, so `fetch` on any NMVideoPlayer
    // ever built threw, and no sidecar could have been loaded whatever else was
    // right.
    fetcher: Fetcher? = null,
    /**
     * Where this player's plugins keep what they remember.
     *
     * Forwarded rather than hidden, which it was. Core's default became a
     * PERSISTENT store — that is the whole point of it, a subtitle language has
     * to survive a relaunch — and a video player that did not pass this on left
     * every consumer, and every test, sharing one file. Six preferences tests in
     * one class then wrote over each other's keys and two of them failed on a
     * device while passing on the JVM, which is a fixture leaking, not a bug in
     * the plugin they were pointed at.
     *
     * Null keeps the platform's own store.
     */
    storage: Storage? = null,
    /**
     * Whether an item reaching its end moves to the next one.
     *
     * On here and off in music, which is a deliberate asymmetry in the reference
     * rather than an oversight: an episode ending should roll into the next one,
     * and a track ending should not start the album again unless somebody asked
     * for it — music advances only when its AutoAdvancePlugin is mounted.
     *
     * This was not wired at all. Nothing subscribed to `ended` except the media
     * session, which pushes STOPPED, so a queued item played to its end and the
     * player sat there with the next item still in the queue. Every source gate
     * in the campaign stayed green over it, because the queue surface it would
     * have called is fully present — nothing was ever going to call it.
     */
    autoAdvance: Boolean = true,
    /**
     * Whatever owns a remote session, which for this library is
     * [tv.nomercy.player.video.cast.VideoCastPlugin].
     *
     * Core took one and this did not forward it, so a video consumer had no way
     * to supply one at all: `transferTo` found nothing, emitted
     * TransferPrevented("no cast sender") and returned false, and handing
     * playback to a television or taking it back was unreachable from the only
     * player that has a cast plugin.
     *
     * The plugin is both this and a plugin, so it is passed here and added:
     * `NMVideoPlayer(backend, castSender = plugin).addPlugin(plugin)`.
     */
    castSender: CastSender? = null,
) : ComposedPlayer(
    backend,
    video = video,
    scope = scope,
    fetcher = fetcher,
    storage = storage,
    castSender = castSender,
) {

    // A video backend is both, so a caller with one says so once.
    public constructor(video: VideoBackend) : this(video, video)

    private var fullscreenActive: Boolean = false
    private var pipActive: Boolean = false
    private var theaterActive: Boolean = false
    private var stretching: Stretching = Stretching.Uniform
    private var rect: VideoRect? = null

    // Held rather than read from the constructor parameter inside the listener,
    // so the field is initialised before init subscribes.
    private val advanceOnEnd: Boolean = autoAdvance

    // The video half of what the engine reports. Core's bridge handles the
    // transport events; these are the ones only a video player has a name for.
    public val videoBridge: VideoBackendBridge = VideoBackendBridge(context)

    // A sidecar file the engine cannot reach, fetched and timed here.
    //
    // The web kit owns this seam for the same reason: the file is named on the
    // ITEM, so nothing below the player knows it exists, and the renderer above
    // must not have to care where a cue came from.
    private val sidecarCues: SidecarSubtitleCues = SidecarSubtitleCues(this, playerScope)

    init {
        register(this)
        backend?.let { videoBridge.attach(it) }

        // Once the engine has populated its lists, which is what mediaReady
        // says. Asking any earlier reads two empty lists and picks nothing.
        context.on(CoreEvents.MediaReady) {
            applyDefaultTracks()
            announceLevels()
        }

        // The item's own subtitle files, registered the moment it becomes the
        // one playing. Without this a host would have to call addSubtitleTrack
        // for every film it queues, and the fields the server already sends
        // would be data nothing read.
        context.on(CoreEvents.Item) { adoptItemSubtitles() }

        // The item's own chapter markers, taken the same way its subtitles are.
        //
        // Without this an item could not carry chapters by any route: a host had
        // to call chapters(list) by hand for every item it queued, and one that
        // did not had a chapter bar with no segments and a next-chapter button
        // that moved nothing — which is how chapter skip can look broken while
        // every chapter unit test passes.
        context.on(CoreEvents.Item) { adoptItemChapters() }

        // Launched rather than called: next() suspends, and an event listener
        // that could suspend would make every emitter on this bus suspending.
        // The scope is the player's own, so a dispose cancels an advance that is
        // still in flight instead of loading an item into a disposed player.
        context.on(CoreEvents.Ended) {
            if (advanceOnEnd) playerScope.launch { next() }
        }
    }

    // The item's own subtitle files, taken as this player's external tracks.
    //
    // Replaced rather than appended: the previous film's files have nothing to
    // do with this one, and leaving them would offer a viewer captions for a
    // film they are no longer watching.
    //
    // De-duplicated by url, not by language. Two files in one language are two
    // real choices — a full translation and a signs-only track are the everyday
    // pair — and collapsing them would silently drop every variant after the
    // first. The web deduplicates the item's own list on exactly this field and
    // says so.
    private fun adoptItemChapters() {
        val current = item() as? VideoPlaylistItem ?: return

        val inline: List<Chapter> = current.chapters
        if (inline.isNotEmpty()) {
            chapters(inline)
            return
        }

        val file: String = current.chapterFile ?: return

        // Fetched off the player's own scope: the item event is synchronous and
        // a listener that suspended would make every emitter on the bus
        // suspending. The item is re-read after the fetch because a viewer can
        // skip on while it is in flight, and publishing a previous item's
        // chapters over the current one is worse than publishing none.
        playerScope.launch {
            val parsed: List<Chapter> = runCatching {
                ChapterCues.parse(fetch(file, FetchOptions()).body)
            }.getOrElse { emptyList() }

            if (parsed.isNotEmpty() && (item() as? VideoPlaylistItem)?.id == current.id) {
                chapters(parsed)
            }
        }
    }

    private fun adoptItemSubtitles() {
        // The outgoing item's sidecar stops here, before anything reads the new
        // one. The reference drops it in core's queue seam ahead of emitting
        // `item`; this is the first Item listener the player registers, so a
        // plugin sees the same already-dropped state it does.
        //
        // Clearing the track LIST is not enough on its own. A sidecar is a
        // CueTracker subscribed to `time`, and a tracker nobody stopped goes on
        // matching the outgoing film's cues against the incoming film's
        // playhead — which draws the previous title's dialogue over an item
        // that may carry no subtitles at all, straight past the overlay's own
        // clear-on-item, because the tracker repaints immediately after it.
        sidecarCues.dispose()

        externalSubtitles = (item() as? VideoPlaylistItem)
            ?.subtitles
            .orEmpty()
            .filter { !it.url.isNullOrBlank() }
            .distinctBy { it.url }

        emit(CoreEvents.Subtitles, SubtitlesPayload(subtitles()))
    }

    // Select the subtitle and audio tracks the host named a language for.
    //
    // No match leaves the engine's own pick alone and says nothing: a viewer who
    // asked for French on a film that has no French subtitles is not owed an
    // error, and a player that turned captions off to signal the miss would be
    // making the absence worse than it is.
    // The engine's ladder, published as the event a quality menu is built from.
    //
    // VideoBackendBridge.announceLevels was public, emitted VideoEvents.Levels
    // and was called by nothing at all — declared-never-emitted, the same class
    // as beforeLoad. A consumer following the reference and waiting for `levels`
    // waited forever while qualityLevels() sat there holding the answer.
    //
    // Announced from mediaReady because that is already the moment this player
    // reads the engine's lists: the comment beside applyDefaultTracks says
    // asking any earlier gets two empty ones, and a ladder is no different.
    //
    // Silent on an empty ladder. A progressive file has no rungs, and an empty
    // `levels` would tell a menu to rebuild itself around nothing.
    private fun announceLevels() {
        val ladder: List<QualityLevel> = qualityLevels()
        if (ladder.isEmpty()) return

        videoBridge.announceLevels(
            ladder.map { level ->
                QualityDescriptor(
                    height = level.height,
                    width = level.width,
                    bitrate = level.bitrate,
                    // Two DynamicRange enums, one per layer, and they carry
                    // the same strings — so the token is the conversion, and a
                    // hand-written when() would be four branches that drift the
                    // day a fifth range is added to one of them.
                    dynamicRange = DynamicRange.fromToken(level.dynamicRange.wire),
                    codec = level.codec,
                )
            },
        )
    }

    private fun applyDefaultTracks() {
        val config: PlayerConfig = options()

        config.defaultSubtitleLanguage?.let { wanted ->
            val tracks: List<SubtitleTrack> = subtitles()
            matchLanguage(tracks.map { it.language }, wanted)
                ?.let { index -> subtitle(tracks[index]) }
        }

        config.defaultAudioLanguage?.let { wanted ->
            val tracks: List<AudioTrack> = audioTracks()
            matchLanguage(tracks.map { it.language }, wanted)
                // Not awaited, because the reference does not await it either:
                // _applyDefaultTracks is a void method calling an async setter.
                ?.let { index -> playerScope.launch { audioTrack(tracks[index]) } }
        }
    }

    public open fun fullscreen(): Boolean = fullscreenActive

    public open fun fullscreen(active: Boolean) {
        if (fullscreenActive == active) return
        fullscreenActive = active
        // Leaving fullscreen and entering theater are different intents, and a
        // chrome cannot be in both. Fullscreen wins because it is the more
        // specific request.
        if (active) theaterActive = false
        emit(VideoEvents.Fullscreen, ViewModeChange(active))
    }

    public open fun toggleFullscreen(): Unit = fullscreen(!fullscreenActive)

    public open fun pip(): Boolean = pipActive

    public open fun pip(active: Boolean) {
        if (pipActive == active) return
        pipActive = active
        emit(VideoEvents.Pip, ViewModeChange(active))
    }

    public open fun togglePip(): Unit = pip(!pipActive)

    public open fun theater(): Boolean = theaterActive

    public open fun theater(active: Boolean) {
        if (theaterActive == active) return
        theaterActive = active
        if (active) fullscreenActive = false
        emit(VideoEvents.Theater, ViewModeChange(active))
    }

    public open fun toggleTheater(): Unit = theater(!theaterActive)

    public open fun aspectRatio(): Stretching = stretching

    public open fun aspectRatio(value: Stretching) {
        if (stretching == value) return
        stretching = value
        emit(VideoEvents.AspectRatio, AspectRatioChange(value))
    }

    // Steps through the modes in the order a viewer pressing one button expects,
    // which is why it is a method rather than something every chrome reimplements.
    public open fun cycleAspectRatio() {
        val modes: List<Stretching> = Stretching.entries
        aspectRatio(modes[(modes.indexOf(stretching) + 1) % modes.size])
    }

    public open fun videoRect(): VideoRect? = rect

    // Reported by whatever is drawing, because only it knows. Null until
    // something has been rendered, which an overlay positioning itself has to
    // handle rather than assume zero.
    public open fun videoRect(value: VideoRect?) {
        if (rect == value) return
        rect = value
        emit(VideoEvents.VideoRect, VideoRectChange(value))
    }

    // Plays a named region and says when the playhead leaves it.
    //
    // The window is the point. A "skip intro" button needs to appear while the
    // intro is playing and disappear when it ends, and only something watching
    // the playhead knows when that is. Seeking to the start rather than jumping
    // the queue means a refused seek refuses the whole thing and the viewer
    // stays where they were.
    //
    // Replaces any window already open. Two watchers on one playhead both fire,
    // and the second one's boundary is about a region the viewer has left.
    // [onEnd] decides what the boundary does, which the caller has always been
    // entitled to say and this always ignored: every window behaved as Advance,
    // so a preview loop ran once and a hold ran straight past the frame it was
    // supposed to stop on.
    public open suspend fun playSegment(
        segment: SegmentBoundary,
        onEnd: SegmentEndBehaviour = SegmentEndBehaviour.Advance,
    ) {
        clearSegment()
        time(segment.startTime)

        val generation: Long = ++segmentGeneration
        segmentWatch = on(CoreEvents.Time) { update ->
            if (update.time >= segment.endTime) closeWindow(segment, onEnd, generation)
        }
    }

    // Announced first, then acted on — the reference's order, and the one a
    // chrome needs: a "skip intro" button has to come down at the boundary
    // whether the window loops, holds or lets playback run on.
    //
    // Loop leaves the window open, so it repeats until something clears it.
    // The other two close it, but only if the listener that just ran did not
    // open a window of its own — hence the generation check, which is the whole
    // reason the old code cleared before announcing.
    private fun closeWindow(segment: SegmentBoundary, onEnd: SegmentEndBehaviour, generation: Long) {
        emit(VideoEvents.SegmentBoundary, segment)

        playerScope.launch {
            when (onEnd) {
                SegmentEndBehaviour.Loop -> time(segment.startTime)
                SegmentEndBehaviour.Hold -> pause()
                SegmentEndBehaviour.Advance -> Unit
            }
            if (onEnd != SegmentEndBehaviour.Loop && generation == segmentGeneration) clearSegment()
        }
    }

    private var segmentGeneration: Long = 0L

    // Stops watching for the end of the current region.
    //
    // A chrome dismissing its own "skip intro" button calls this, and so does
    // anything that changed the item underneath a window that is still open —
    // a watcher left running across a track change fires on a timestamp that
    // means something else now.
    public open fun clearSegment() {
        segmentWatch?.dispose()
        segmentWatch = null
    }

    private var segmentWatch: Subscription? = null

    // ── Subtitles ────────────────────────────────────────────────────────────

    // Whether the subtitle showing was chosen or fell out of the engine's
    // defaults, exactly as audioTrackMode answers for audio.
    //
    // A chrome needs it to decide whether to tick a language in a menu, and a
    // per-library player needs it to know whether to carry a choice into the
    // next episode or let the engine decide again.
    public open fun subtitleState(): AudioTrackState = subtitleChoice

    // How subtitles should be drawn.
    //
    // Held here and announced rather than applied, because core has no renderer:
    // libass draws them on three platforms and a Compose overlay on none of
    // them. The renderer listens and restyles; this is the one place the
    // viewer's preference lives so every renderer reads the same answer.
    public open fun subtitleStyle(): SubtitleStyle = cueStyle

    public open fun subtitleStyle(style: SubtitleStyle) {
        cueStyle = style
        emit(CoreEvents.SubtitleStyle, style)
    }

    // The next subtitle track, wrapping through off.
    //
    // Off is part of the cycle rather than a separate control, because that is
    // what a remote's subtitle button does: press it enough times and the
    // subtitles go away. A cycle that never reached off would trap a viewer who
    // turned them on by accident.
    public open fun cycleSubtitles() {
        val available: List<SubtitleTrack?> = subtitles() + listOf(null)
        if (available.size <= 1) return

        val here: Int = available.indexOfFirst { it?.id == subtitle()?.id }
        subtitle(available[(here + 1) % available.size])
        subtitleChoice = AudioTrackState.MANUAL
    }

    // The next audio track, wrapping. No off: an item with no audio is not a
    // state a viewer can ask for, and a cycle that produced silence would look
    // like a broken track rather than a choice.
    public open fun cycleAudioTracks() {
        val available: List<AudioTrack> = audioTracks()
        if (available.size <= 1) return

        val here: Int = available.indexOfFirst { it.id == audioTrack()?.id }
        // Same as the reference: cycleAudioTracks returns void and does not
        // await the selection it starts.
        playerScope.launch { audioTrack(available[(here + 1) % available.size]) }
    }

    // A subtitle file the item did not come with.
    //
    // The everyday case is a sidecar the viewer downloaded, or one the server
    // found after the item was already playing. Added to what the engine
    // reported rather than replacing it, and kept here rather than pushed into
    // the engine, because not every engine accepts a track after loading and
    // the ones that refuse would silently drop it.
    public open fun addSubtitleTrack(track: SubtitleTrack) {
        // With nothing loaded there is nothing for the track to belong to, and
        // it used to be kept anyway — so a sidecar added early was silently
        // attached to whatever item happened to load next.
        if (item() == null) {
            throw stateError(
                CoreErrorCodes.NO_ACTIVE_ITEM,
                "addSubtitleTrack() called with no active item.",
            )
        }

        externalSubtitles = externalSubtitles.filterNot { it.id == track.id } + track
        emit(CoreEvents.Subtitles, SubtitlesPayload(subtitles()))
    }

    public open fun removeSubtitleTrack(id: String) {
        val without: List<SubtitleTrack> = externalSubtitles.filterNot { it.id == id }
        if (without.size == externalSubtitles.size) return

        externalSubtitles = without
        // Showing a track that has just been removed is the failure this
        // prevents: the renderer keeps drawing cues from a file nobody can
        // select any more.
        if (subtitle()?.id == id) subtitle(null)
        emit(CoreEvents.Subtitles, SubtitlesPayload(subtitles()))
    }

    // What the engine reported, plus what the host added.
    //
    // An engine track displaced when a sidecar covers the same language. The
    // viewer chooses a language once, and the file somebody put beside the film
    // is the one they meant — which is the rule the web states at this seam and
    // the reason a NoMercy item with a directory of .vtt does not show every
    // language twice.
    override fun subtitles(): List<SubtitleTrack> {
        val sidecars: List<SubtitleTrack> = externalSubtitles
        if (sidecars.isEmpty()) return super.subtitles()

        val covered: Set<String> = sidecars.mapTo(mutableSetOf()) { subtitleTrackKey(it) }
        return super.subtitles().filter { subtitleTrackKey(it) !in covered } + sidecars
    }

    // The sidecar being played, when one is, because the engine cannot answer
    // for a track it never reported. Core reads its answer straight off the
    // engine, so without this a viewer who chose a .vtt saw no tick beside it in
    // the menu and the next episode could not carry the choice forward.
    override fun subtitle(): SubtitleTrack? = sidecarCues.active ?: super.subtitle()

    // The sidecar's fetch and its subscription to the playhead die with the
    // player. The scope they run on is the player's, so nothing would leak past
    // a disposal, but a tracker still advancing while the player is being torn
    // down emits into listeners that are being removed underneath it.
    override suspend fun dispose(opts: ActionOptions) {
        sidecarCues.dispose()
        super.dispose(opts)
    }

    // One selection, whichever kind of track it is.
    //
    // A sidecar and an engine track are mutually exclusive on purpose. Two
    // producers feeding one cue channel puts two sets of captions on one
    // picture, so choosing a file turns the engine's own text off, and choosing
    // an engine track stops the file.
    override fun subtitle(track: SubtitleTrack?) {
        val isSidecar: Boolean = sidecarCues.select(track)
        super.subtitle(if (isSidecar) null else track)
    }

    // The rung the viewer ASKED for, which is not the rung that ends up playing.
    //
    // `quality:requested` was declared in VideoEvents and emitted by nothing, so
    // a consumer listening for an explicit pick heard silence and the shipped
    // preferences plugin could never save one. It cannot be folded into the
    // level-switch the engine already reports: adaptation switches rungs all
    // evening without the viewer touching anything, and a listener that could
    // not tell those apart would record every ABR decision as a choice and drag
    // the viewer out of Auto for good.
    //
    // Null is Auto, here as everywhere else in the ladder API.
    override fun quality(level: QualityLevel?) {
        super.quality(level)
        val chosen: Int? = level?.let { pick: QualityLevel -> qualityLevels().indexOf(pick).takeIf { it >= 0 } }
        emit(VideoEvents.QualityRequested, QualityRequest(chosen))
    }

    // The seam every item passes through on the way into the queue.
    //
    // Video items arrive from a server that sends what it has, and what it has
    // differs by scan age: a title in one field or another, a duration in
    // seconds or milliseconds. Normalising here rather than at each call site
    // means a consumer building a queue by hand gets the same treatment as one
    // loading a playlist.
    //
    // Identity by default. Core cannot know a wire format it was never told
    // about, and a normaliser that guessed would corrupt the fields it guessed
    // wrong.
    public open fun normalizePlaylistItem(item: PlaylistItem): PlaylistItem = item

    private var subtitleChoice: AudioTrackState = AudioTrackState.DEFAULT
    private var cueStyle: SubtitleStyle = SubtitleStyle()
    private var externalSubtitles: List<SubtitleTrack> = emptyList()

    public open fun message(text: String, ms: Double? = null) {
        emit(VideoEvents.DisplayMessage, DisplayMessage(text, ms))
    }

    public open fun clearMessage() {
        emit(VideoEvents.RemoveMessage, Unit)
    }

    public companion object {
        private val live: MutableList<NMVideoPlayer> = mutableListOf()

        // Every player built, so a host with more than one on screen can find
        // them without threading a reference through its whole UI. The web trio
        // keeps the same registry for the same reason.
        public fun instances(): List<NMVideoPlayer> = live.toList()

        private fun register(player: NMVideoPlayer) {
            live.add(player)
        }
    }
}
