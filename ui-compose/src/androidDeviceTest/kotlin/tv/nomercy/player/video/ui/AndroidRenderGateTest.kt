// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import tv.nomercy.player.core.ports.ExoPlayerVideoBackend
import tv.nomercy.player.core.ports.LoadOptions
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

private const val MEDIA_FILE = "render-gate.mp4"
private const val FIRST_FRAME_TIMEOUT_S = 20L

// G1 on Android: does the engine paint into the surface the view mounted.
//
// Not a screenshot. Media3 draws into a SurfaceView, which is a hole punched
// through the view hierarchy rather than pixels in it — capturing the view gives
// back the hole, and a gate reading that would report black on a working player
// and black on a broken one.
//
// onRenderedFirstFrame is the engine's own statement that it wrote a frame to
// the surface it was given. It does not fire without a surface, so it is exactly
// the claim G1 makes: the view mounted a real one and the decoder reached it.
// Replacing the mounted view with an empty composable fails this test, which is
// how that claim was checked rather than assumed.
//
// Run on a phone (SM-A137F, API 34) and on an Android TV box (Nokia Streaming
// Box 8010, API 34). CI has no device attached, so this gate runs from a
// developer machine against real hardware and the CI matrix does not pretend to
// cover it.
class AndroidRenderGateTest {

    @get:Rule
    val compose = createComposeRule()

    private fun mediaOnDevice(context: Context): File =
        writeTestClip(File(context.cacheDir, MEDIA_FILE))

    @Test
    fun theEngineRendersItsFirstFrameIntoTheMountedSurface() {
        val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val media: File = mediaOnDevice(appContext)
        val backend = ExoPlayerVideoBackend(appContext)
        val painted = CountDownLatch(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            backend.exoPlayer.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() = painted.countDown()
            })
        }

        try {
            compose.setContent {
                PlayerSurface(VideoSurface(backend), Modifier.fillMaxSize())
            }
            compose.waitForIdle()

            runBlocking {
                backend.load(media.absolutePath, LoadOptions())
                backend.play()
            }

            assertTrue(
                painted.await(FIRST_FRAME_TIMEOUT_S, TimeUnit.SECONDS),
                "the engine never reported a rendered frame: the view mounted no usable surface",
            )
        } finally {
            backend.release()
            media.delete()
        }
    }
}
