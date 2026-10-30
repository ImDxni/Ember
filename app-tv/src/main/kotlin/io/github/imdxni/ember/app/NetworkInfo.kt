/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.app

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkInfo {
    /** IPv4 address of the active network, or null when offline. */
    fun localIpv4(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm?.activeNetwork?.let { cm.getLinkProperties(it) }?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.let { return it.hostAddress }
        // Fallback: first non-loopback IPv4 on any interface that is up.
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address }?.hostAddress
        }.getOrNull()
    }
}
