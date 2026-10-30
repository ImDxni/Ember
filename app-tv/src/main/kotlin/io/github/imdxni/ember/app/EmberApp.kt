/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.app

import android.app.Application
import io.github.imdxni.ember.protocol.raop.RaopProtocolFactory

class EmberApp : Application() {
    lateinit var controller: ReceiverController
        private set

    override fun onCreate() {
        super.onCreate()
        val registry = ProtocolRegistry().apply {
            register(RaopProtocolFactory())
            // Future input protocols are registered here, e.g. register(EmberProtocolFactory()).
        }
        controller = ReceiverController(this, registry, ReceiverSettings(this), AndroidLog)
        AndroidLog.i("app", "Ember ${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID}) starting")
    }
}
