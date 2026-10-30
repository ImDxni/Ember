/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Native logging to logcat. All tags are prefixed with "Ember/" so that
 * logcat filters on the "Ember/" prefix catch Kotlin and native logs.
 */
#ifndef EMBER_LOG_H
#define EMBER_LOG_H

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    EMBER_LOG_VERBOSE,
    EMBER_LOG_DEBUG,
    EMBER_LOG_INFO,
    EMBER_LOG_WARN,
    EMBER_LOG_ERROR,
} ember_log_level_t;

void ember_log(ember_log_level_t level, const char *tag, const char *fmt, ...)
    __attribute__((format(printf, 3, 4)));

/* Callback for UxPlay's logger (raop_set_log_callback); maps syslog levels. */
void ember_log_uxplay(void *cls, int level, const char *msg);

#ifdef __cplusplus
}
#endif
#endif
