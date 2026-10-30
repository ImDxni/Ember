/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.api

/** Announces network services (mDNS/DNS-SD) on behalf of protocols. */
public interface ServiceAdvertiser {
    /** Registration is asynchronous; the outcome is reported to [callback]. */
    public fun register(service: ServiceDescriptor, callback: AdvertiseCallback): Registration
}

public class ServiceDescriptor(
    /** Instance name, e.g. "Ember" or "AABBCCDDEEFF@Ember". */
    public val instanceName: String,
    /** Service type without domain, e.g. "_raop._tcp". */
    public val type: String,
    public val port: Int,
    /** TXT record entries in announcement order; a null value means a key without "=". */
    public val txt: List<Pair<String, ByteArray?>>,
) {
    override fun toString(): String = "ServiceDescriptor($instanceName $type:$port txt=${txt.size} keys)"
}

/** A live announcement. [close] withdraws it. */
public interface Registration : AutoCloseable {
    /** Name actually announced (may differ after conflict resolution), null until registered. */
    public val actualName: String?
}

public interface AdvertiseCallback {
    public fun onRegistered(actualName: String)

    public fun onFailed(error: ReceiverError)
}
