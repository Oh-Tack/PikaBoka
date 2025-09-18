// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 8.6.0 사용 (Android Studio 호환)
        classpath("com.android.tools.build:gradle:8.6.0")
    }
}
