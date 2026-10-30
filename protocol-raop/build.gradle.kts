import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

val basePackage = providers.gradleProperty("ember.basePackage").get()
val moduleNamespace = "$basePackage.protocol.raop"

// ---- Native dependencies (OpenSSL libcrypto, libplist) -------------------
// Built by third_party/build-native-deps.sh. If third_party/prebuilt/ is
// missing or outdated, the pinned bundle is downloaded and checksum-verified.
val thirdParty = rootProject.layout.projectDirectory.dir("third_party")
val prebuiltDir = thirdParty.dir("prebuilt").asFile
val nativeAbis = listOf("armeabi-v7a", "arm64-v8a")
val depsProps = Properties().apply {
    thirdParty.file("native-deps.properties").asFile.inputStream().use(::load)
}

val prepareNativeDeps = tasks.register("prepareNativeDeps") {
    group = "build setup"
    description = "Ensures third_party/prebuilt contains OpenSSL and libplist for all ABIs."
    val expectedVersion = depsProps.getProperty("NATIVE_DEPS_VERSION")
    val bundleUrl = depsProps.getProperty("BUNDLE_URL")
    val bundleSha = depsProps.getProperty("BUNDLE_SHA256").orEmpty()
    val prebuilt = prebuiltDir
    val abis = nativeAbis
    val cacheDir = layout.buildDirectory.dir("native-deps").get().asFile
    doLast {
        val versionFile = prebuilt.resolve("BUNDLE_VERSION")
        val complete = versionFile.isFile && versionFile.readText().trim() == expectedVersion &&
            abis.all { prebuilt.resolve("$it/lib/libcrypto.a").isFile && prebuilt.resolve("$it/lib/libplist-2.0.a").isFile }
        if (complete) return@doLast

        if (bundleSha.isBlank()) {
            throw GradleException(
                "Native dependencies v$expectedVersion not found in $prebuilt and no prebuilt bundle " +
                    "checksum is pinned yet. Build them with:\n" +
                    "  wsl bash third_party/build-native-deps.sh   (Windows)\n" +
                    "  bash third_party/build-native-deps.sh       (Linux/macOS, ANDROID_NDK_ROOT set)",
            )
        }
        cacheDir.mkdirs()
        val archive = cacheDir.resolve(bundleUrl.substringAfterLast('/'))
        logger.lifecycle("Downloading native dependencies: $bundleUrl")
        URI(bundleUrl).toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (!digest.equals(bundleSha, ignoreCase = true)) {
            archive.delete()
            throw GradleException("Checksum mismatch for $bundleUrl: expected $bundleSha, got $digest")
        }
        prebuilt.deleteRecursively()
        // tar is available on Linux, macOS and Windows 10+.
        val proc = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", prebuilt.parentFile.absolutePath)
            .inheritIO().start()
        if (proc.waitFor() != 0) throw GradleException("Failed to extract $archive")
    }
}

android {
    namespace = moduleNamespace
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += nativeAbis
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DEMBER_DEPS_DIR=${prebuiltDir.absolutePath.replace('\\', '/')}",
                    "-DEMBER_JNI_CLASS=${moduleNamespace.replace('.', '/')}/NativeBridge",
                    "-DANDROID_STL=none",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild") { dependsOn(prepareNativeDeps) }

dependencies {
    api(project(":receiver-api"))
}
