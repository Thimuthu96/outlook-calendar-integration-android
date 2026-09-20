// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9.0+ has Kotlin support built in; org.jetbrains.kotlin.android is intentionally not
    // applied here (see app/build.gradle.kts).
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}