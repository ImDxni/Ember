/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.api

/**
 * Implemented by the host (the app). Decides which sender owns the screen
 * when several protocols are running, and hands out the [MediaSink].
 */
public interface SessionHost {
    /**
     * Called by a protocol when a sender connects ("connected" event).
     * Returns null if the host rejects the session (e.g. busy); the protocol
     * must then refuse the sender.
     */
    public fun openSession(info: SessionInfo, control: SessionControl): MediaSession?
}

public data class SessionInfo(
    val protocolId: String,
    /** Unique within the protocol, e.g. the sender's connection id. */
    val sessionId: String,
    val peerName: String?,
    val peerAddress: String?,
    val peerModel: String?,
)

/** A granted session. The protocol must end it with exactly one of [close] or [fail]. */
public interface MediaSession {
    public val info: SessionInfo
    public val sink: MediaSink

    /** The sender disconnected normally ("disconnected" event). */
    public fun close(reason: DisconnectReason)

    /** The session ended because of an error ("error" event). */
    public fun fail(error: ReceiverError)
}

/** Lets the host act on a session owned by a protocol. */
public interface SessionControl {
    /** Ask the protocol to drop the sender (user pressed Back, or preemption). */
    public fun disconnect()
}

public enum class DisconnectReason {
    /** The sender stopped mirroring. */
    SENDER_STOPPED,
    /** The connection was lost (timeout, network change). */
    CONNECTION_LOST,
    /** The host asked to disconnect via [SessionControl.disconnect]. */
    HOST_REQUEST,
    /** Another sender took over. */
    REPLACED,
    /** The receiver is shutting down. */
    RECEIVER_STOPPED,
}
