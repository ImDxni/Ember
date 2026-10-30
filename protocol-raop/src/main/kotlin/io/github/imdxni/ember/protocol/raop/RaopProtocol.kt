/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.protocol.raop

import io.github.imdxni.ember.api.DeviceCapabilities
import io.github.imdxni.ember.api.ProtocolContext
import io.github.imdxni.ember.api.ProtocolFactory
import io.github.imdxni.ember.api.ReceiverConfig
import io.github.imdxni.ember.api.ReceiverError
import io.github.imdxni.ember.api.ReceiverProtocol
import io.github.imdxni.ember.api.VideoCodec

/** Mirroring from iPhone/iPad/Mac (compatible with AirPlay screen mirroring), via UxPlay. */
public class RaopProtocolFactory : ProtocolFactory {
    override val id: String = ID
    override val displayName: String = "iPhone / iPad screen mirroring"

    // Mirroring needs at least an H.264 decoder; ABIs are filtered by the APK itself.
    override fun isSupported(capabilities: DeviceCapabilities): Boolean = capabilities.supports(VideoCodec.H264)

    override fun create(): ReceiverProtocol = RaopProtocol()

    public companion object {
        public const val ID: String = "raop"
    }
}

/**
 * Stage 2 skeleton: loads the native library and runs its self-test
 * (pairing key + TXT records, no sockets). The RTSP server, discovery and
 * the MediaSink renderer are wired in the next stages.
 */
internal class RaopProtocol : ReceiverProtocol {
    override val id: String = RaopProtocolFactory.ID
    private var context: ProtocolContext? = null

    /** Result of the last native self-test, for diagnostics screens. */
    @Volatile
    var lastSelfTest: String? = null
        private set

    override fun start(context: ProtocolContext) {
        this.context = context
        val log = context.log
        try {
            NativeBridge.ensureLoaded()
            log.i(TAG, "native: ${NativeBridge.nativeVersionInfo()}")
            val keyFile = context.storageDir.resolve("pairing.pem").absolutePath
            val result = NativeBridge.nativeSelfTest(
                context.config.name,
                context.config.deviceId.toByteArray(),
                keyFile,
            )
            lastSelfTest = result
            if (!result.startsWith("OK")) {
                context.listener.onError(id, ReceiverError(ReceiverError.Code.NATIVE_FAILURE, result))
                return
            }
            context.listener.onStarted(id)
        } catch (t: Throwable) {
            log.e(TAG, "native start failed", t)
            context.listener.onError(id, ReceiverError(ReceiverError.Code.NATIVE_FAILURE, t.message ?: "native failure", t))
        }
    }

    override fun stop() {
        context?.listener?.onStopped(id)
        context = null
    }

    override fun onConfigChanged(config: ReceiverConfig) {
        context?.log?.i(TAG, "config changed: name=${config.name} (applied on next start)")
    }

    private companion object {
        const val TAG = "raop"
    }
}

/** Exposes the native self-test result of a protocol created by [RaopProtocolFactory]. */
public fun ReceiverProtocol.raopSelfTestResult(): String? = (this as? RaopProtocol)?.lastSelfTest

/** Native library description, e.g. "UxPlay lib 534e2cc (220.68), OpenSSL 3.5.9 ..., libplist 2.8.0". */
public fun raopNativeVersion(): String = runCatching {
    NativeBridge.ensureLoaded()
    NativeBridge.nativeVersionInfo()
}.getOrElse { "unavailable: ${it.message}" }
