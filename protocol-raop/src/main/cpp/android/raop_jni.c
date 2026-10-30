/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * JNI entry points of libember_raop. Natives are bound with RegisterNatives
 * to EMBER_JNI_CLASS (set by CMake from the Gradle namespace), so renaming
 * the Kotlin package never requires touching this file.
 */
#include <jni.h>
#include <stdio.h>
#include <string.h>

#include <openssl/crypto.h>

#include "dnssd.h"
#include "global.h"
#include "raop.h"

#include "ember_log.h"

#define TAG "raop-jni"

#ifndef EMBER_JNI_CLASS
#error "EMBER_JNI_CLASS must be defined by the build"
#endif

static jstring native_version_info(JNIEnv *env, jclass clazz)
{
    (void) clazz;
    char buf[256];
    snprintf(buf, sizeof(buf), "UxPlay lib %s (%s), %s, libplist %s",
             EMBER_UXPLAY_COMMIT, GLOBAL_VERSION, OpenSSL_version(OPENSSL_VERSION), EMBER_LIBPLIST_VERSION);
    return (*env)->NewStringUTF(env, buf);
}

static void selftest_audio(void *cls, raop_ntp_t *ntp, audio_decode_struct *data)
{
    (void) cls; (void) ntp; (void) data;
}

static void selftest_video(void *cls, raop_ntp_t *ntp, video_decode_struct *data)
{
    (void) cls; (void) ntp; (void) data;
}

/*
 * Exercises the native stack without opening sockets: creates the dnssd and
 * raop objects, generates (or loads) the Ed25519 pairing key with OpenSSL and
 * builds both TXT records. Returns a one-line report, or "FAIL: ..." on error.
 */
static jstring native_self_test(JNIEnv *env, jclass clazz, jstring jname, jbyteArray jhwaddr, jstring jkeyfile)
{
    (void) clazz;
    char report[512];
    const char *name = (*env)->GetStringUTFChars(env, jname, NULL);
    const char *keyfile = (*env)->GetStringUTFChars(env, jkeyfile, NULL);
    jsize hw_len = (*env)->GetArrayLength(env, jhwaddr);
    char hwaddr[MAX_HWADDR_LEN] = {0};
    char device_id[3 * MAX_HWADDR_LEN] = {0};
    dnssd_t *dnssd = NULL;
    raop_t *raop = NULL;
    int error = 0;

    if (hw_len != MAX_HWADDR_LEN) {
        snprintf(report, sizeof(report), "FAIL: hw address must be %d bytes", MAX_HWADDR_LEN);
        goto out;
    }
    (*env)->GetByteArrayRegion(env, jhwaddr, 0, hw_len, (jbyte *) hwaddr);
    for (int i = 0; i < MAX_HWADDR_LEN; i++) {
        snprintf(device_id + 3 * i, 4, i < MAX_HWADDR_LEN - 1 ? "%02x:" : "%02x", (unsigned char) hwaddr[i]);
    }

    dnssd = dnssd_init(name, (int) strlen(name), hwaddr, hw_len, 0, &error);
    if (!dnssd) {
        snprintf(report, sizeof(report), "FAIL: dnssd_init error %d", error);
        goto out;
    }

    raop_callbacks_t callbacks;
    memset(&callbacks, 0, sizeof(callbacks));
    callbacks.audio_process = selftest_audio;
    callbacks.video_process = selftest_video;
    raop = raop_init(&callbacks);
    if (!raop) {
        snprintf(report, sizeof(report), "FAIL: raop_init");
        goto out;
    }
    raop_set_log_callback(raop, ember_log_uxplay, NULL);
    raop_set_log_level(raop, LOGGER_DEBUG);
    if (raop_init2(raop, 1, device_id, keyfile)) {
        snprintf(report, sizeof(report), "FAIL: raop_init2 (pairing key / httpd)");
        goto out;
    }
    raop_set_dnssd(raop, dnssd);

    /* Builds the TXT records; with no advertiser hook installed nothing is announced. */
    dnssd_register_raop(dnssd, 7000);
    dnssd_register_airplay(dnssd, 7000);
    int raop_txt_len = 0, airplay_txt_len = 0;
    dnssd_get_raop_txt(dnssd, &raop_txt_len);
    dnssd_get_airplay_txt(dnssd, &airplay_txt_len);
    dnssd_unregister_raop(dnssd);
    dnssd_unregister_airplay(dnssd);

    snprintf(report, sizeof(report), "OK: deviceid=%s pk=%.16s... features=0x%llX txt raop=%dB airplay=%dB",
             device_id, dnssd->pk ? dnssd->pk : "?", (unsigned long long) dnssd_get_airplay_features(dnssd),
             raop_txt_len, airplay_txt_len);

out:
    if (raop) raop_destroy(raop);
    if (dnssd) dnssd_destroy(dnssd);
    (*env)->ReleaseStringUTFChars(env, jname, name);
    (*env)->ReleaseStringUTFChars(env, jkeyfile, keyfile);
    ember_log(report[0] == 'O' ? EMBER_LOG_INFO : EMBER_LOG_ERROR, TAG, "self-test: %s", report);
    return (*env)->NewStringUTF(env, report);
}

static const JNINativeMethod methods[] = {
    {"nativeVersionInfo", "()Ljava/lang/String;", (void *) native_version_info},
    {"nativeSelfTest", "(Ljava/lang/String;[BLjava/lang/String;)Ljava/lang/String;", (void *) native_self_test},
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void) reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = (*env)->FindClass(env, EMBER_JNI_CLASS);
    if (!clazz) {
        ember_log(EMBER_LOG_ERROR, TAG, "class %s not found", EMBER_JNI_CLASS);
        return JNI_ERR;
    }
    if ((*env)->RegisterNatives(env, clazz, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) {
        ember_log(EMBER_LOG_ERROR, TAG, "RegisterNatives failed for %s", EMBER_JNI_CLASS);
        return JNI_ERR;
    }
    ember_log(EMBER_LOG_INFO, TAG, "loaded, natives bound to %s", EMBER_JNI_CLASS);
    return JNI_VERSION_1_6;
}
