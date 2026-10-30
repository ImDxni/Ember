/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.api

import java.security.SecureRandom

/** Error reported by protocols, advertisers and sinks. */
public class ReceiverError(
    public val code: Code,
    public val message: String,
    public val cause: Throwable? = null,
) {
    public enum class Code {
        PORT_UNAVAILABLE,
        ADVERTISE_FAILED,
        NATIVE_FAILURE,
        PROTOCOL_ERROR,
        UNSUPPORTED_FORMAT,
        DECODER_FAILURE,
        INTERNAL,
    }

    override fun toString(): String = "ReceiverError($code: $message${cause?.let { " / $it" } ?: ""})"
}

/** Logging facade so pure-Kotlin modules can log without depending on Android. */
public interface EmberLog {
    public fun d(tag: String, message: String)

    public fun i(tag: String, message: String)

    public fun w(tag: String, message: String, error: Throwable? = null)

    public fun e(tag: String, message: String, error: Throwable? = null)
}

/**
 * Stable 6-byte receiver identifier, formatted like a MAC address. Android
 * no longer exposes the real MAC, so the app generates one per installation
 * (locally administered, unicast) and persists it.
 */
public class DeviceId private constructor(private val bytes: ByteArray) {
    public fun toByteArray(): ByteArray = bytes.copyOf()

    /** "AA:BB:CC:DD:EE:FF" */
    override fun toString(): String = bytes.joinToString(":") { "%02X".format(it) }

    override fun equals(other: Any?): Boolean = other is DeviceId && other.bytes.contentEquals(bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    public companion object {
        public const val SIZE: Int = 6

        public fun random(random: SecureRandom = SecureRandom()): DeviceId {
            val b = ByteArray(SIZE).also(random::nextBytes)
            // Locally administered (bit 1 set), unicast (bit 0 clear).
            b[0] = ((b[0].toInt() and 0xFC) or 0x02).toByte()
            return DeviceId(b)
        }

        /** Parses "AA:BB:CC:DD:EE:FF" (also accepts "-" or no separator). */
        public fun parse(text: String): DeviceId {
            val hex = text.filter { it.isLetterOrDigit() }
            require(hex.length == SIZE * 2) { "invalid device id: $text" }
            return DeviceId(ByteArray(SIZE) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() })
        }
    }
}
