plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = providers.gradleProperty("ember.basePackage").get() + ".media"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":receiver-api"))
}
