// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.video.cast

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

// One call for the whole of casting.
//
// An application should not have to know that a control client, a controller and
// a plugin are three objects, or which order they go together in. It supplies
// the television it found and a way to get a token; everything below is the
// library's problem.
//
// The same call serves every platform. The transport is portable, and the waker
// resolves to whatever this platform can actually do — which off Android is
// nothing, because there is no panel to wake.
public fun videoCastPlugin(
    host: String,
    scope: CoroutineScope,
    port: Int = DEFAULT_TV_PORT,
    token: suspend () -> String? = { null },
    http: HttpClient = defaultCastHttpClient(),
    waker: CastWaker = defaultCastWaker(),
): VideoCastPlugin {
    val client = KtorTvControlClient(host = host, port = port, http = http, token = token)

    return VideoCastPlugin(RemoteVideoController(client, scope), scope, waker)
}

// The client the library builds when an application does not supply one.
//
// Unknown keys are ignored, which is the compatibility rule the whole cast
// surface rests on: a television running a newer build sends fields this one has
// never heard of, and a strict reader would refuse to control a working set.
public fun defaultCastHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}

// What a NoMercy television listens on. Named rather than repeated at every call
// site, because a viewer never types it and a caller should not have to know it.
public const val DEFAULT_TV_PORT: Int = 7626
