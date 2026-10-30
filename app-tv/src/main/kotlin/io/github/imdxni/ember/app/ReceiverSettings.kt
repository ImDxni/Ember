/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.app

import android.content.Context
import io.github.imdxni.ember.api.DeviceId
import io.github.imdxni.ember.api.ReceiverConfig
import io.github.imdxni.ember.api.VideoSize

/** Persistent receiver settings. The device id is generated once per installation. */
class ReceiverSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ember", Context.MODE_PRIVATE)

    val deviceId: DeviceId
        get() = prefs.getString(KEY_DEVICE_ID, null)?.let(DeviceId::parse)
            ?: DeviceId.random().also { prefs.edit().putString(KEY_DEVICE_ID, it.toString()).apply() }

    var name: String
        get() = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        set(value) = prefs.edit().putString(KEY_NAME, value.trim()).apply()

    var maxVideo: VideoSize
        get() = VideoSize(prefs.getInt(KEY_MAX_W, 1920), prefs.getInt(KEY_MAX_H, 1080))
        set(value) = prefs.edit().putInt(KEY_MAX_W, value.width).putInt(KEY_MAX_H, value.height).apply()

    fun toConfig(): ReceiverConfig = ReceiverConfig(name, deviceId, maxVideo, maxFps = 60)

    private companion object {
        const val DEFAULT_NAME = "Ember"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_NAME = "name"
        const val KEY_MAX_W = "max_width"
        const val KEY_MAX_H = "max_height"
    }
}
