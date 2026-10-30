/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import io.github.imdxni.ember.api.VideoCodec
import io.github.imdxni.ember.protocol.raop.raopNativeVersion

/**
 * Full-screen receiver screen: a SurfaceView for the mirrored video (stage 5)
 * under a waiting overlay with the receiver name and address.
 */
class MainActivity : Activity() {
    private lateinit var nameView: TextView
    private lateinit var addressView: TextView
    private lateinit var statusView: TextView
    private lateinit var detailsView: TextView
    private val controller get() = (application as EmberApp).controller

    private val listener = ReceiverController.Listener { render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        nameView = findViewById(R.id.receiver_name)
        addressView = findViewById(R.id.receiver_address)
        statusView = findViewById(R.id.receiver_status)
        detailsView = findViewById(R.id.receiver_details)
        ReceiverService.start(this)
    }

    override fun onStart() {
        super.onStart()
        hideSystemUi()
        controller.addListener(listener)
    }

    override fun onStop() {
        controller.removeListener(listener)
        super.onStop()
    }

    override fun onDestroy() {
        // For now the receiver lives only while the app is open (Back = stop).
        if (isFinishing) ReceiverService.stop(this)
        super.onDestroy()
    }

    private fun render(state: ReceiverController.State) {
        nameView.text = state.receiverName.ifEmpty { getString(R.string.app_name) }
        val ip = NetworkInfo.localIpv4(this)
        addressView.text = ip?.let { getString(R.string.address_format, it) } ?: getString(R.string.no_network)
        statusView.text = when (state.status) {
            ReceiverController.Status.STOPPED -> getString(R.string.status_stopped)
            ReceiverController.Status.STARTING -> getString(R.string.status_starting)
            ReceiverController.Status.RUNNING -> getString(R.string.status_waiting)
            ReceiverController.Status.ERROR -> getString(R.string.status_error)
        }
        detailsView.text = buildString {
            append("v").append(BuildConfig.VERSION_NAME)
            state.capabilities?.let { caps ->
                append(" · API ").append(caps.sdkInt).append(" · ").append(caps.abis.firstOrNull())
                caps.bestDecoder(VideoCodec.H264)?.let {
                    append(" · H.264 ").append(it.maxWidth).append('x').append(it.maxHeight)
                }
            }
            append('\n').append(raopNativeVersion())
            state.protocols.forEach { (id, status) -> append('\n').append(id).append(": ").append(status) }
        }
    }

    @Suppress("DEPRECATION") // WindowInsetsController requires API 30; minSdk is 25.
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
