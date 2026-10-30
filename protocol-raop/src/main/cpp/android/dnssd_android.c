/**
 *  Copyright (C) 2011-2012  Juho Vähä-Herttua
 *  Copyright (C) 2026  kgbook
 *  Copyright (C) 2026  Ember contributors
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *=================================================================
 * Derived from UxPlay lib/mdnsd/dnssd_mdnsd.c (fduncanh, kgbook):
 * the TXT record contents are kept identical; the mDNS responder is
 * replaced by a hook into the Android ServiceAdvertiser.
 */

#include <assert.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "dnssd.h"
#include "dnssdint.h"
#include "global.h"
#include "utils.h"

#include "dnssd_android.h"
#include "ember_log.h"

#define TAG "dnssd"
#define TXT_MAX_BYTES 1024
#define NAME_MAX_BYTES 256

typedef struct {
    unsigned char bytes[TXT_MAX_BYTES];
    int length;
} txt_record_t;

typedef struct dnssd_private_s {
    txt_record_t raop_record;
    txt_record_t airplay_record;
    char raop_name[NAME_MAX_BYTES];
    char airplay_name[NAME_MAX_BYTES];
    int raop_registered;
    int airplay_registered;
} dnssd_private_t;

static pthread_mutex_t hook_mutex = PTHREAD_MUTEX_INITIALIZER;
static dnssd_android_hook_t hook;
static int hook_set;

void dnssd_android_set_hook(const dnssd_android_hook_t *new_hook)
{
    pthread_mutex_lock(&hook_mutex);
    if (new_hook) {
        hook = *new_hook;
        hook_set = 1;
    } else {
        memset(&hook, 0, sizeof(hook));
        hook_set = 0;
    }
    pthread_mutex_unlock(&hook_mutex);
}

static int txt_add(txt_record_t *txt, const char *key, const char *value)
{
    size_t entry_len = strlen(key) + 1 + strlen(value);
    if (entry_len > 255 || txt->length + 1 + (int) entry_len > TXT_MAX_BYTES) {
        return -1;
    }
    txt->bytes[txt->length++] = (unsigned char) entry_len;
    txt->length += snprintf((char *) txt->bytes + txt->length, TXT_MAX_BYTES - txt->length,
                            "%s=%s", key, value);
    return 0;
}

static int dnssd_build_raop_txt(dnssd_t *dnssd_public)
{
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    char features[22] = {0};
    const char *pw = "false";
    const char *sf = RAOP_SF;

    switch (dnssd_public->pin_pw) {
    case 1:
        pw = "true";
        sf = "0x8c";
        break;
    case 2:
    case 3:
        pw = "true";
        sf = "0x84";
        break;
    default:
        break;
    }

    snprintf(features, sizeof(features), "0x%X,0x%X", dnssd_public->features1, dnssd_public->features2);
    dnssd->raop_record.length = 0;

    return txt_add(&dnssd->raop_record, "ch", RAOP_CH) ||
           txt_add(&dnssd->raop_record, "cn", RAOP_CN) ||
           txt_add(&dnssd->raop_record, "da", RAOP_DA) ||
           txt_add(&dnssd->raop_record, "et", RAOP_ET) ||
           txt_add(&dnssd->raop_record, "vv", RAOP_VV) ||
           txt_add(&dnssd->raop_record, "ft", features) ||
           txt_add(&dnssd->raop_record, "am", GLOBAL_MODEL) ||
           txt_add(&dnssd->raop_record, "md", RAOP_MD) ||
           txt_add(&dnssd->raop_record, "rhd", RAOP_RHD) ||
           txt_add(&dnssd->raop_record, "pw", pw) ||
           txt_add(&dnssd->raop_record, "sr", RAOP_SR) ||
           txt_add(&dnssd->raop_record, "ss", RAOP_SS) ||
           txt_add(&dnssd->raop_record, "sv", RAOP_SV) ||
           txt_add(&dnssd->raop_record, "tp", RAOP_TP) ||
           txt_add(&dnssd->raop_record, "txtvers", RAOP_TXTVERS) ||
           txt_add(&dnssd->raop_record, "sf", sf) ||
           txt_add(&dnssd->raop_record, "vs", RAOP_VS) ||
           txt_add(&dnssd->raop_record, "vn", RAOP_VN) ||
           txt_add(&dnssd->raop_record, "pk", dnssd_public->pk ? dnssd_public->pk : "");
}

static int dnssd_build_airplay_txt(dnssd_t *dnssd_public)
{
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    char device_id[3 * MAX_HWADDR_LEN];
    char features[22] = {0};

    if (utils_hwaddr_airplay(device_id, sizeof(device_id), dnssd_public->hw_addr, dnssd_public->hw_addr_len) < 0) {
        return -1;
    }

    snprintf(features, sizeof(features), "0x%X,0x%X", dnssd_public->features1, dnssd_public->features2);
    dnssd->airplay_record.length = 0;

    return txt_add(&dnssd->airplay_record, "deviceid", device_id) ||
           txt_add(&dnssd->airplay_record, "features", features) ||
           txt_add(&dnssd->airplay_record, "pw", dnssd_public->pin_pw ? "true" : "false") ||
           txt_add(&dnssd->airplay_record, "flags", "0x4") ||
           txt_add(&dnssd->airplay_record, "model", GLOBAL_MODEL) ||
           txt_add(&dnssd->airplay_record, "pk", dnssd_public->pk ? dnssd_public->pk : "") ||
           txt_add(&dnssd->airplay_record, "pi", AIRPLAY_PI) ||
           txt_add(&dnssd->airplay_record, "srcvers", AIRPLAY_SRCVERS) ||
           txt_add(&dnssd->airplay_record, "vv", AIRPLAY_VV);
}

void *
dnssd_private_init(dnssd_t *dnssd_public, int *error)
{
    char hwaddr[2 * MAX_HWADDR_LEN + 1];
    if (error) *error = DNSSD_ERROR_NOERROR;

    dnssd_private_t *dnssd = (dnssd_private_t *) calloc(1, sizeof(dnssd_private_t));
    if (!dnssd) {
        if (error) *error = DNSSD_ERROR_OUTOFMEM;
        return NULL;
    }
    if (utils_hwaddr_raop(hwaddr, sizeof(hwaddr), dnssd_public->hw_addr, dnssd_public->hw_addr_len) < 0) {
        free(dnssd);
        if (error) *error = DNSSD_ERROR_HWADDRLEN;
        return NULL;
    }
    /* Instance names without the "._raop._tcp.local" suffix: NsdManager adds type and domain. */
    snprintf(dnssd->raop_name, sizeof(dnssd->raop_name), "%s@%s", hwaddr, dnssd_public->name);
    snprintf(dnssd->airplay_name, sizeof(dnssd->airplay_name), "%s", dnssd_public->name);
    return dnssd;
}

void
dnssd_private_destroy(void *private_data)
{
    free(private_data);
}

static int forward_register(int service, const char *name, const char *type,
                            unsigned short port, const txt_record_t *txt)
{
    int ret = 0;
    pthread_mutex_lock(&hook_mutex);
    if (hook_set && hook.register_service) {
        ret = hook.register_service(hook.cls, service, name, type, port, txt->bytes, txt->length);
    } else {
        ember_log(EMBER_LOG_WARN, TAG, "no advertiser hook: %s (%s:%u) not announced", name, type, port);
    }
    pthread_mutex_unlock(&hook_mutex);
    return ret;
}

static void forward_unregister(int service)
{
    pthread_mutex_lock(&hook_mutex);
    if (hook_set && hook.unregister_service) {
        hook.unregister_service(hook.cls, service);
    }
    pthread_mutex_unlock(&hook_mutex);
}

int
dnssd_register_raop(dnssd_t *dnssd_public, unsigned short port)
{
    assert(dnssd_public && dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (dnssd_build_raop_txt(dnssd_public)) {
        return -1;
    }
    int ret = forward_register(EMBER_SERVICE_RAOP, dnssd->raop_name, "_raop._tcp", port, &dnssd->raop_record);
    dnssd->raop_registered = (ret == 0);
    return ret;
}

int
dnssd_register_airplay(dnssd_t *dnssd_public, unsigned short port)
{
    assert(dnssd_public && dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (dnssd_build_airplay_txt(dnssd_public)) {
        return -1;
    }
    int ret = forward_register(EMBER_SERVICE_AIRPLAY, dnssd->airplay_name, "_airplay._tcp", port,
                               &dnssd->airplay_record);
    dnssd->airplay_registered = (ret == 0);
    return ret;
}

void
dnssd_unregister_raop(dnssd_t *dnssd_public)
{
    assert(dnssd_public && dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (dnssd->raop_registered) {
        forward_unregister(EMBER_SERVICE_RAOP);
        dnssd->raop_registered = 0;
    }
}

void
dnssd_unregister_airplay(dnssd_t *dnssd_public)
{
    assert(dnssd_public && dnssd_public->dnssd_private);
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    if (dnssd->airplay_registered) {
        forward_unregister(EMBER_SERVICE_AIRPLAY);
        dnssd->airplay_registered = 0;
    }
}

const char *
dnssd_get_raop_txt(dnssd_t *dnssd_public, int *length)
{
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    *length = dnssd->raop_record.length;
    return (const char *) dnssd->raop_record.bytes;
}

const char *
dnssd_get_airplay_txt(dnssd_t *dnssd_public, int *length)
{
    dnssd_private_t *dnssd = (dnssd_private_t *) dnssd_public->dnssd_private;
    *length = dnssd->airplay_record.length;
    return (const char *) dnssd->airplay_record.bytes;
}

/* No daemon socket to service: announcements are owned by the Java side. */
int
dnssd_get_service_fd(dnssd_t *dnssd_public, int service)
{
    (void) dnssd_public;
    (void) service;
    return -1;
}

int
dnssd_process_service(dnssd_t *dnssd_public, int service)
{
    (void) dnssd_public;
    (void) service;
    return 0;
}

void
dnssd_error_text(int *dnssd_error, const char *appname)
{
    ember_log(EMBER_LOG_ERROR, TAG, "%s: service announcement failed (error %d)",
              appname ? appname : "ember", dnssd_error ? *dnssd_error : 0);
}
