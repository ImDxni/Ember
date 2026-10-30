/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.imdxni.ember.api.AdvertiseCallback
import io.github.imdxni.ember.api.DeviceCapabilities
import io.github.imdxni.ember.api.EmberLog
import io.github.imdxni.ember.api.MediaSession
import io.github.imdxni.ember.api.ProtocolContext
import io.github.imdxni.ember.api.ProtocolListener
import io.github.imdxni.ember.api.ReceiverError
import io.github.imdxni.ember.api.ReceiverProtocol
import io.github.imdxni.ember.api.Registration
import io.github.imdxni.ember.api.ServiceAdvertiser
import io.github.imdxni.ember.api.ServiceDescriptor
import io.github.imdxni.ember.api.SessionControl
import io.github.imdxni.ember.api.SessionHost
import io.github.imdxni.ember.api.SessionInfo
import io.github.imdxni.ember.media.AndroidCapabilitiesProvider
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Starts and stops every registered protocol and publishes a UI-friendly
 * [State]. Protocol work runs on a dedicated thread; listeners are called on
 * the main thread.
 */
class ReceiverController(
    private val context: Context,
    private val registry: ProtocolRegistry,
    private val settings: ReceiverSettings,
    private val log: EmberLog,
) {
    enum class Status { STOPPED, STARTING, RUNNING, ERROR }

    data class State(
        val status: Status = Status.STOPPED,
        val receiverName: String = "",
        /** Per-protocol status line, keyed by protocol id. */
        val protocols: Map<String, String> = emptyMap(),
        val capabilities: DeviceCapabilities? = null,
    )

    fun interface Listener {
        fun onStateChanged(state: State)
    }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ember-receiver") }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val running = mutableListOf<ReceiverProtocol>()

    @Volatile
    var state: State = State()
        private set

    fun addListener(listener: Listener) {
        listeners += listener
        listener.onStateChanged(state)
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun start() = worker.execute {
        if (running.isNotEmpty()) return@execute
        val config = settings.toConfig()
        update { State(Status.STARTING, config.name) }

        val caps = AndroidCapabilitiesProvider(context).detect()
        log.i(TAG, "capabilities: sdk=${caps.sdkInt} abis=${caps.abis} display=${caps.display}")
        caps.video.forEach { log.i(TAG, "  video decoder: $it") }
        log.i(TAG, "  audio codecs: ${caps.audio}")
        update { it.copy(capabilities = caps) }

        for (factory in registry.all()) {
            if (!factory.isSupported(caps)) {
                log.w(TAG, "protocol ${factory.id} not supported on this device")
                setProtocolStatus(factory.id, "not supported on this device")
                continue
            }
            val protocol = factory.create()
            val storage = context.filesDir.resolve("protocols/${factory.id}").apply { mkdirs() }
            val protocolContext = ProtocolContext(
                config = config,
                capabilities = caps,
                advertiser = PendingAdvertiser,
                sessions = RejectingSessionHost,
                storageDir = storage,
                listener = protocolListener,
                log = log,
            )
            log.i(TAG, "starting protocol ${factory.id}")
            setProtocolStatus(factory.id, "starting")
            protocol.start(protocolContext)
            running += protocol
        }
        update {
            val failed = it.protocols.values.any { s -> s.startsWith("error") }
            it.copy(status = if (failed) Status.ERROR else Status.RUNNING)
        }
    }

    fun stop() = worker.execute {
        running.forEach { protocol ->
            runCatching { protocol.stop() }.onFailure { log.e(TAG, "stop ${protocol.id} failed", it) }
        }
        running.clear()
        update { State(Status.STOPPED, it.receiverName) }
    }

    private val protocolListener = object : ProtocolListener {
        override fun onStarted(protocolId: String) {
            log.i(TAG, "protocol $protocolId started")
            setProtocolStatus(protocolId, "ready")
        }

        override fun onStopped(protocolId: String) {
            log.i(TAG, "protocol $protocolId stopped")
            setProtocolStatus(protocolId, "stopped")
        }

        override fun onError(protocolId: String, error: ReceiverError) {
            log.e(TAG, "protocol $protocolId error: $error", error.cause)
            setProtocolStatus(protocolId, "error: ${error.message}")
        }
    }

    private fun setProtocolStatus(id: String, status: String) = update { it.copy(protocols = it.protocols + (id to status)) }

    @Synchronized
    private fun update(transform: (State) -> State) {
        val newState = transform(state)
        state = newState
        main.post { listeners.forEach { it.onStateChanged(newState) } }
    }

    /** Placeholder until :discovery provides the real advertiser (stage 3). */
    private object PendingAdvertiser : ServiceAdvertiser {
        override fun register(service: ServiceDescriptor, callback: AdvertiseCallback): Registration {
            callback.onFailed(ReceiverError(ReceiverError.Code.ADVERTISE_FAILED, "discovery not implemented yet"))
            return object : Registration {
                override val actualName: String? = null
                override fun close() = Unit
            }
        }
    }

    /** Placeholder until :media-core provides the renderer (stage 5). */
    private object RejectingSessionHost : SessionHost {
        override fun openSession(info: SessionInfo, control: SessionControl): MediaSession? = null
    }

    private companion object {
        const val TAG = "controller"
    }
}
