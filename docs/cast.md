# Casting to a NoMercy television

Casting sends a film to a television and turns the phone into a remote control.
It is not screen mirroring and it is not streaming through a Chromecast: the set
fetches and plays the film with its own player, and this device sends it commands
over the set's control protocol.

That distinction is the whole design. While a cast is running, every local
transport action is intercepted and prevented before it reaches the engine here,
then sent to the television instead — so this device never decodes a frame and
never plays a second copy of the film into the room.

## Using it

```kotlin
val plugin = videoCastPlugin(
    host = device.host,
    port = device.port,
    scope = playerScope,
    token = { session.accessToken },
)

player.addPlugin(plugin)
plugin.startCast(device, deepLinkFor(item))
```

`device` comes from `defaultDeviceDiscovery()`, which browses the local network.
Everything below that call — a control client, a controller, a plugin — is
assembled for you, and the same call works on every platform.

## What each platform can do

| | Find a television | Wake a sleeping panel | Control one |
| --- | --- | --- | --- |
| Android | yes | not built | yes |
| iOS and tvOS | not built | not applicable | yes |
| Desktop | not built | not applicable | yes |

Control is portable because the protocol is ordinary HTTP and an event stream.
Finding and waking are not, and where they are missing they answer with an empty
list or an unsupported outcome rather than an error — a television reached by
some other route still works.

Waking is Android-only by nature: it goes through a Chromecast to turn the panel
on and start the application, and no other platform has that path. It is
deliberately wake-only and never loads media, because media reaching the set that
way would be the second renderer this design exists to avoid.

## Talking to an older or newer television

Self-hosted installs update on their own schedule, so the two ends being
different ages is the normal case.

Every field the set sends has a default here, so an older television's partial
frame still decodes. Unknown fields are ignored, so a newer one's extra data does
not break anything. A frame this build does not recognise is named rather than
dropped silently. And a malformed frame is skipped rather than thrown, because
the event stream is one long connection — an exception would end the whole
subscription over a single bad frame, leaving the set playing and this device no
longer listening.

One case is deliberately conservative. A playback state this build has never
heard of drops that frame rather than falling back to a default, because the
default is idle and a television that is playing drawn as stopped is worse than
one drawn a few seconds out of date.

## Casting and Connect are not the same thing

Connect is music across several devices over a server-authoritative SignalR hub.
Cast is video to one television over its own HTTP protocol. They share nothing
below the plugin base class, and folding them together would give both the
constraints of the other.
