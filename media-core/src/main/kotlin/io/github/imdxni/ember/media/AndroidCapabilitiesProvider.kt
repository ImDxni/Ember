/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.media

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import io.github.imdxni.ember.api.AudioCodec
import io.github.imdxni.ember.api.CapabilitiesProvider
import io.github.imdxni.ember.api.DeviceCapabilities
import io.github.imdxni.ember.api.DisplayInfo
import io.github.imdxni.ember.api.VideoCodec
import io.github.imdxni.ember.api.VideoDecoderInfo

/**
 * Detects decoders and display through public Android APIs (MediaCodecList,
 * DisplayManager). No device-model checks: what the codecs report is what we use.
 */
public class AndroidCapabilitiesProvider(context: Context) : CapabilitiesProvider {
    private val appContext = context.applicationContext

    override fun detect(): DeviceCapabilities {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        return DeviceCapabilities(
            video = codecs.flatMap(::videoDecoders),
            audio = audioCodecs(codecs),
            display = display(),
            abis = Build.SUPPORTED_ABIS.toList(),
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    private fun videoDecoders(info: MediaCodecInfo): List<VideoDecoderInfo> =
        VideoCodec.entries.mapNotNull { codec ->
            val type = info.supportedTypes.firstOrNull { it.equals(codec.mimeType, ignoreCase = true) }
                ?: return@mapNotNull null
            val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: return@mapNotNull null
            val video = caps.videoCapabilities ?: return@mapNotNull null
            val maxW = video.supportedWidths.upper
            val maxH = video.supportedHeights.upper
            // Highest frame rate at the largest standard size the decoder accepts.
            val fps = listOf(maxW to maxH, 3840 to 2160, 1920 to 1080, 1280 to 720)
                .firstNotNullOfOrNull { (w, h) ->
                    runCatching { video.getSupportedFrameRatesFor(w, h).upper.toInt() }.getOrNull()
                } ?: video.supportedFrameRates.upper
            VideoDecoderInfo(
                name = info.name,
                codec = codec,
                maxWidth = maxW,
                maxHeight = maxH,
                maxFps = fps,
                hardware = isHardware(info),
                adaptivePlayback = caps.isFeatureSupported(CodecCapabilities.FEATURE_AdaptivePlayback),
                lowLatency = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    caps.isFeatureSupported(CodecCapabilities.FEATURE_LowLatency),
            )
        }

    private fun audioCodecs(codecs: List<MediaCodecInfo>): Set<AudioCodec> {
        val types = codecs.flatMap { it.supportedTypes.map(String::lowercase) }.toSet()
        val result = mutableSetOf(AudioCodec.PCM_S16)
        // The platform AAC decoder handles both LC and ELD; ELD is confirmed at decoder setup.
        if (AudioCodec.AAC_LC.mimeType in types) {
            result += AudioCodec.AAC_LC
            result += AudioCodec.AAC_ELD
        }
        return result
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isHardwareAccelerated
        } else {
            // Before API 29 the platform's software codecs are recognisable by their AOSP name prefix.
            val name = info.name.lowercase()
            !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw."))
        }

    private fun display(): DisplayInfo {
        val dm = appContext.getSystemService(DisplayManager::class.java)
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: return DisplayInfo(1920, 1080, 60f)
        val mode = display.mode
        return DisplayInfo(mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
    }
}
