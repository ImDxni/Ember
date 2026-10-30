/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.api

/**
 * What this device can play, detected at runtime. Device differences must
 * go through this class, never through model or brand checks.
 */
public data class DeviceCapabilities(
    val video: List<VideoDecoderInfo>,
    /** Audio codecs the sink can play, including software decoders it ships. */
    val audio: Set<AudioCodec>,
    val display: DisplayInfo,
    val abis: List<String>,
    val sdkInt: Int,
) {
    public fun supports(codec: VideoCodec): Boolean = video.any { it.codec == codec }

    public fun supports(codec: AudioCodec): Boolean = codec in audio

    /** Largest size any decoder for [codec] handles, preferring hardware decoders. */
    public fun maxSize(codec: VideoCodec): VideoSize? =
        bestDecoder(codec)?.let { VideoSize(it.maxWidth, it.maxHeight) }

    public fun bestDecoder(codec: VideoCodec): VideoDecoderInfo? =
        video.filter { it.codec == codec }
            .maxWithOrNull(compareBy<VideoDecoderInfo> { it.hardware }.thenBy { it.maxWidth.toLong() * it.maxHeight })
}

public data class VideoDecoderInfo(
    val name: String,
    val codec: VideoCodec,
    val maxWidth: Int,
    val maxHeight: Int,
    /** Frame rate supported at [maxWidth] x [maxHeight]. */
    val maxFps: Int,
    val hardware: Boolean,
    val adaptivePlayback: Boolean,
    val lowLatency: Boolean,
)

public data class DisplayInfo(
    val width: Int,
    val height: Int,
    val refreshRateHz: Float,
)

/** Detects [DeviceCapabilities]; the Android implementation lives in :media-core. */
public fun interface CapabilitiesProvider {
    public fun detect(): DeviceCapabilities
}
