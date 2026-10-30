/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.protocol.raop

/**
 * Bindings to libember_raop (UxPlay lib + Android glue). Natives are
 * registered in JNI_OnLoad (see src/main/cpp/android/raop_jni.c).
 */
internal object NativeBridge {
    private var loadError: Throwable? = null

    init {
        try {
            System.loadLibrary("ember_raop")
        } catch (t: UnsatisfiedLinkError) {
            loadError = t
        }
    }

    /** Throws if the native library could not be loaded on this device/ABI. */
    fun ensureLoaded() {
        loadError?.let { throw IllegalStateException("libember_raop not available", it) }
    }

    @JvmStatic
    external fun nativeVersionInfo(): String

    /** See native_self_test in raop_jni.c. Returns "OK: ..." or "FAIL: ...". */
    @JvmStatic
    external fun nativeSelfTest(name: String, hwAddr: ByteArray, keyFile: String): String
}
