# libember_raop: UxPlay's protocol library (uxplay/lib, vendored unmodified)
# + Ember's Android glue (android/). Built by Gradle for each ABI.
#
# Required cache variables (passed by protocol-raop/build.gradle.kts):
#   EMBER_DEPS_DIR   third_party/prebuilt (contains <abi>/include, <abi>/lib)
#   EMBER_JNI_CLASS  JNI path of the Kotlin NativeBridge class
cmake_minimum_required(VERSION 3.22)
project(ember_raop C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_EXTENSIONS ON)

set(UXPLAY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/uxplay/lib)
set(DEPS_DIR ${EMBER_DEPS_DIR}/${ANDROID_ABI})

foreach(dep lib/libcrypto.a lib/libplist-2.0.a include/openssl/crypto.h include/plist/plist.h)
    if(NOT EXISTS ${DEPS_DIR}/${dep})
        message(FATAL_ERROR "Missing native dependency ${DEPS_DIR}/${dep}. "
                "Run third_party/build-native-deps.sh (Linux/WSL) or let Gradle download the prebuilt bundle.")
    endif()
endforeach()

# Version strings shown in the app / logs.
file(STRINGS ${DEPS_DIR}/VERSIONS PLIST_VERSION_LINE REGEX "^libplist=")
string(REPLACE "libplist=" "" EMBER_LIBPLIST_VERSION "${PLIST_VERSION_LINE}")
file(STRINGS ${CMAKE_CURRENT_SOURCE_DIR}/uxplay/UPSTREAM.md UXPLAY_COMMIT_LINE REGEX "^- Commit:")
string(REGEX MATCH "[0-9a-f]+" UXPLAY_COMMIT_FULL "${UXPLAY_COMMIT_LINE}")
string(SUBSTRING "${UXPLAY_COMMIT_FULL}" 0 7 EMBER_UXPLAY_COMMIT)

add_library(ember_crypto STATIC IMPORTED)
set_target_properties(ember_crypto PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/lib/libcrypto.a)
add_library(ember_plist STATIC IMPORTED)
set_target_properties(ember_plist PROPERTIES IMPORTED_LOCATION ${DEPS_DIR}/lib/libplist-2.0.a)

file(GLOB UXPLAY_SOURCES CONFIGURE_DEPENDS
        ${UXPLAY_DIR}/*.c
        ${UXPLAY_DIR}/llhttp/*.c
        ${UXPLAY_DIR}/playfair/*.c)
file(GLOB EMBER_SOURCES CONFIGURE_DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/android/*.c)

# Sources are compiled straight into the shared library (no intermediate
# static archive), so every UxPlay symbol must resolve at link time.
add_library(ember_raop SHARED ${UXPLAY_SOURCES} ${EMBER_SOURCES})

target_include_directories(ember_raop PRIVATE
        ${UXPLAY_DIR}
        ${UXPLAY_DIR}/llhttp
        ${UXPLAY_DIR}/playfair
        ${CMAKE_CURRENT_SOURCE_DIR}/android
        ${DEPS_DIR}/include)

# Same definitions as UxPlay's lib/CMakeLists.txt for Linux; libplist >= 2.3.
target_compile_definitions(ember_raop PRIVATE
        STANDALONE __STDC_CONSTANT_MACROS __STDC_LIMIT_MACROS TARGET_POSIX _LINUX _REENTRANT
        NOHOLD PLIST_210 PLIST_230
        OPENSSL_API_COMPAT=0x10101000L
        EMBER_JNI_CLASS="${EMBER_JNI_CLASS}"
        EMBER_UXPLAY_COMMIT="${EMBER_UXPLAY_COMMIT}"
        EMBER_LIBPLIST_VERSION="${EMBER_LIBPLIST_VERSION}")

target_compile_options(ember_raop PRIVATE -Wall -O2 -fvisibility=hidden
        # Vendored code is kept unmodified; silence warnings we cannot fix there.
        -Wno-unused-function -Wno-unused-variable -Wno-unused-but-set-variable)

target_link_libraries(ember_raop PRIVATE ember_plist ember_crypto log m)

target_link_options(ember_raop PRIVATE
        -Wl,--no-undefined
        -Wl,--gc-sections
        -Wl,--exclude-libs,ALL
        # 16 KB page alignment (required on newer Android TV devices, harmless on Fire OS).
        -Wl,-z,max-page-size=16384)
