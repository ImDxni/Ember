import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: no Android dependency, so protocols and sinks can be
// unit-tested on the host and reused by non-Android front-ends.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
}
