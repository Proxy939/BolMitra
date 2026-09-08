// Root build file. Plugins are declared here with `apply false` and applied per-module.
// Root build file. Plugins declared with `apply false`, applied per-module.
//
// NOTE: there is deliberately no `org.jetbrains.kotlin.android` plugin here.
// AGP 9.0+ ships built-in Kotlin support (android.builtInKotlin defaults true) and
// applying kotlin-android fails against the new DSL.
// See https://kotl.in/gradle/agp-built-in-kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
