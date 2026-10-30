/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.app

import android.util.Log
import io.github.imdxni.ember.api.EmberLog

/** Logcat backend; tags are "Ember/<tag>", same as native code. */
object AndroidLog : EmberLog {
    private fun tag(tag: String) = "Ember/$tag"

    override fun d(tag: String, message: String) {
        Log.d(tag(tag), message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag(tag), message)
    }

    override fun w(tag: String, message: String, error: Throwable?) {
        Log.w(tag(tag), message, error)
    }

    override fun e(tag: String, message: String, error: Throwable?) {
        Log.e(tag(tag), message, error)
    }
}
