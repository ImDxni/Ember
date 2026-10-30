/*
 * Ember - open source screen mirroring receiver
 * Copyright (C) 2026 Ember contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
#include <android/log.h>
#include <stdarg.h>
#include <stdio.h>

#include "ember_log.h"
#include "logger.h"

static int to_android_priority(ember_log_level_t level)
{
    switch (level) {
    case EMBER_LOG_VERBOSE: return ANDROID_LOG_VERBOSE;
    case EMBER_LOG_DEBUG:   return ANDROID_LOG_DEBUG;
    case EMBER_LOG_INFO:    return ANDROID_LOG_INFO;
    case EMBER_LOG_WARN:    return ANDROID_LOG_WARN;
    default:                return ANDROID_LOG_ERROR;
    }
}

void ember_log(ember_log_level_t level, const char *tag, const char *fmt, ...)
{
    char full_tag[48];
    snprintf(full_tag, sizeof(full_tag), "Ember/%s", tag);
    va_list ap;
    va_start(ap, fmt);
    __android_log_vprint(to_android_priority(level), full_tag, fmt, ap);
    va_end(ap);
}

void ember_log_uxplay(void *cls, int level, const char *msg)
{
    (void) cls;
    ember_log_level_t ember_level;
    if (level <= LOGGER_ERR) {
        ember_level = EMBER_LOG_ERROR;
    } else if (level == LOGGER_WARNING) {
        ember_level = EMBER_LOG_WARN;
    } else if (level <= LOGGER_INFO) {
        ember_level = EMBER_LOG_INFO;
    } else if (level == LOGGER_DEBUG) {
        ember_level = EMBER_LOG_DEBUG;
    } else {
        ember_level = EMBER_LOG_VERBOSE;
    }
    ember_log(ember_level, "uxplay", "%s", msg);
}
