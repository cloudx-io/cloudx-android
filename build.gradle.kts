// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.ksp) apply false
}

// ========================================
// Versioning
// ========================================

val baseVersion = project.findProperty("cloudx.version.base") as String?
    ?: error("cloudx.version.base not found in gradle.properties")

// CI can override version with -Pversion=X.Y.Z-dev.42+abc123
// Local builds get -local suffix automatically
val versionOverride = project.findProperty("version") as String?
val computedVersion = when {
    versionOverride != null && versionOverride != "unspecified" -> versionOverride
    else -> "$baseVersion-local"
}

allprojects {
    group = project.findProperty("cloudx.group.id") as String? ?: "io.cloudx"
    version = computedVersion
}

println("========================================")
println("CloudX SDK Version: $computedVersion")
println("========================================")

true // Needed to make the Suppress annotation work for the plugins block