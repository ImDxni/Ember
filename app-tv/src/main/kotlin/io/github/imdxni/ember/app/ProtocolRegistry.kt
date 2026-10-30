/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.imdxni.ember.app

import io.github.imdxni.ember.api.ProtocolFactory

/** The input protocols compiled into this build, in registration order. */
class ProtocolRegistry {
    private val factories = linkedMapOf<String, ProtocolFactory>()

    fun register(factory: ProtocolFactory) {
        require(factory.id !in factories) { "protocol ${factory.id} registered twice" }
        factories[factory.id] = factory
    }

    fun all(): List<ProtocolFactory> = factories.values.toList()
}
