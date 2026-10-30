/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.api

import java.nio.ByteBuffer

/**
 * Receives protocol-neutral encoded media. Implemented by the renderer
 * (:media-core); protocols only ever see this interface.
 *
 * Contract:
 * - Thread-safe; may be called from any protocol thread, never blocks for long.
 * - A format call ([onVideoFormat], [onAudioFormat]) always precedes the
 *   frames it describes and is repeated whenever the format changes.
 * - [EncodedFrame.data] is only valid during the call: copy what you keep.
 */
public interface MediaSink {
    public fun onVideoFormat(format: VideoFormat)

    public fun onVideoFrame(frame: EncodedFrame)

    public fun onAudioFormat(format: AudioFormat)

    public fun onAudioFrame(frame: EncodedFrame)

    public fun onVolume(volume: Volume)

    /** Drop queued data (e.g. seek, sender flush). */
    public fun onFlush(streams: Set<StreamType>)

    /** The sender paused the video stream (e.g. phone locked) or resumed it. */
    public fun onVideoPaused(paused: Boolean)
}

public enum class StreamType { VIDEO, AUDIO }

public enum class VideoCodec(public val mimeType: String) {
    H264("video/avc"),
    H265("video/hevc"),
}

public enum class AudioCodec(public val mimeType: String) {
    AAC_LC("audio/mp4a-latm"),
    AAC_ELD("audio/mp4a-latm"),
    ALAC("audio/alac"),
    PCM_S16("audio/raw"),
}

public class VideoFormat(
    public val codec: VideoCodec,
    /** Parameter sets in Annex-B form: [SPS, PPS] for H.264, [VPS, SPS, PPS] for H.265. */
    public val csd: List<ByteArray>,
    /** Coded size of the stream. */
    public val width: Int,
    public val height: Int,
    /** Visible area inside the coded size (equal to width/height when there is no crop). */
    public val displayWidth: Int = width,
    public val displayHeight: Int = height,
) {
    override fun toString(): String =
        "VideoFormat($codec ${width}x$height display=${displayWidth}x$displayHeight csd=${csd.map { it.size }})"
}

public class AudioFormat(
    public val codec: AudioCodec,
    public val sampleRate: Int,
    public val channels: Int,
    public val samplesPerFrame: Int,
    /** AudioSpecificConfig for AAC, magic cookie for ALAC, empty for PCM. */
    public val config: ByteArray,
) {
    override fun toString(): String =
        "AudioFormat($codec ${sampleRate}Hz ch=$channels spf=$samplesPerFrame config=${config.size}B)"
}

public class EncodedFrame(
    /** Payload; for video, Annex-B NAL units. Valid only during the sink call. */
    public val data: ByteBuffer,
    /**
     * Presentation time on the receiver's monotonic clock (System.nanoTime()
     * domain), i.e. when the frame should be shown/heard with zero added
     * latency. Each protocol maps its sender clock onto this clock.
     */
    public val ptsNanos: Long,
    public val flags: Int = 0,
) {
    public companion object {
        public const val FLAG_KEY_FRAME: Int = 1
        /** The payload starts with in-band parameter sets. */
        public const val FLAG_INLINE_CONFIG: Int = 2
    }
}

/** Playback volume. Protocols convert their own scale (AirPlay uses dB). */
public data class Volume(
    /** Linear gain, 0.0 (silent) to 1.0 (full). */
    val linear: Float,
    val muted: Boolean,
)

public data class VideoSize(val width: Int, val height: Int) {
    override fun toString(): String = "${width}x$height"
}
