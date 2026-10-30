/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Android backend for UxPlay's dnssd API. It does not touch the network:
 * it builds the TXT records exactly like UxPlay's own backends and hands
 * every (un)registration to a hook, which forwards it to the Kotlin
 * ServiceAdvertiser (module :discovery).
 */
#ifndef EMBER_DNSSD_ANDROID_H
#define EMBER_DNSSD_ANDROID_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define EMBER_SERVICE_RAOP    0
#define EMBER_SERVICE_AIRPLAY 1

typedef struct dnssd_android_hook_s {
    void *cls;
    /* service: EMBER_SERVICE_*; name: instance name; type: "_raop._tcp";
       txt: DNS TXT wire format (length-prefixed "key=value" strings).
       Returns 0 on success. */
    int  (*register_service)(void *cls, int service, const char *name, const char *type,
                             unsigned short port, const unsigned char *txt, int txt_len);
    void (*unregister_service)(void *cls, int service);
} dnssd_android_hook_t;

/* Installs the hook used by all dnssd_t instances; NULL disables announcements. */
void dnssd_android_set_hook(const dnssd_android_hook_t *hook);

#ifdef __cplusplus
}
#endif
#endif
