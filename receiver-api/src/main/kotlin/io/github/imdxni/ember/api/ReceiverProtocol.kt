/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.api

import java.io.File

/**
 * Creates [ReceiverProtocol] instances. The app keeps one factory per input
 * protocol in its registry; adding a protocol means adding a module that
 * provides a factory and registering it, nothing else.
 */
public interface ProtocolFactory {
    /** Stable identifier, e.g. "raop". Used in logs and settings keys. */
    public val id: String

    /** Human readable name shown in the UI. */
    public val displayName: String

    /** Whether this protocol can run on a device with [capabilities]. */
    public fun isSupported(capabilities: DeviceCapabilities): Boolean

    public fun create(): ReceiverProtocol
}

/**
 * An input protocol (a way for a sender to reach the receiver).
 *
 * A protocol owns its sockets and its service announcements, turns what it
 * receives into protocol-neutral media and hands it to a [MediaSink] obtained
 * from [ProtocolContext.sessions]. It never renders anything itself.
 *
 * [start] and [stop] are called from the same thread; implementations must
 * not block it for long (do network work on their own threads).
 */
public interface ReceiverProtocol {
    public val id: String

    public fun start(context: ProtocolContext)

    /** Closes active sessions, withdraws announcements, releases sockets. */
    public fun stop()

    /** Applies a new configuration (e.g. new name) while running. */
    public fun onConfigChanged(config: ReceiverConfig)
}

/** Everything a protocol needs from the host, injected at [ReceiverProtocol.start]. */
public class ProtocolContext(
    public val config: ReceiverConfig,
    public val capabilities: DeviceCapabilities,
    public val advertiser: ServiceAdvertiser,
    public val sessions: SessionHost,
    /** Private, persistent directory for this protocol (keys, ids). */
    public val storageDir: File,
    public val listener: ProtocolListener,
    public val log: EmberLog,
)

/** User-facing receiver configuration shared by all protocols. */
public data class ReceiverConfig(
    /** Name shown on senders, e.g. "Ember" or "Living room". */
    public val name: String,
    /** Stable per-installation identifier, formatted like a MAC address. */
    public val deviceId: DeviceId,
    /** Upper bound requested by the user; the effective limit also depends on [DeviceCapabilities]. */
    public val maxVideo: VideoSize,
    public val maxFps: Int,
)

/** Protocol-level lifecycle events (session events go through [SessionHost]). */
public interface ProtocolListener {
    public fun onStarted(protocolId: String)

    public fun onStopped(protocolId: String)

    public fun onError(protocolId: String, error: ReceiverError)
}
