plugins {
    alias(libs.plugins.android.application)
}

val basePackage = providers.gradleProperty("ember.basePackage").get()

// Release signing comes from the environment (CI secrets). Without it,
// release APKs are signed with the debug key so they stay installable.
val keystorePath: String? = System.getenv("EMBER_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "$basePackage.app"
    compileSdk = libs.versions.compileSdk.get().toInt()
    // Same NDK as :protocol-raop, used to strip native libraries when packaging.
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = providers.gradleProperty("ember.applicationId").get()
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("ember.versionCode").get().toInt()
        versionName = providers.gradleProperty("ember.versionName").get()
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("EMBER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EMBER_KEY_ALIAS")
                keyPassword = System.getenv("EMBER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // One APK per ABI plus a universal APK containing both.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":receiver-api"))
    implementation(project(":media-core"))
    implementation(project(":discovery"))
    // Input protocols. A new protocol = one line here + one line in EmberApp.
    implementation(project(":protocol-raop"))

    implementation(libs.androidx.core)
}
