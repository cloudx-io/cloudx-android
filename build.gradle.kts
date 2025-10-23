// Top-level build file where you can add configuration options common to all sub-projects/modules.
import org.gradle.api.artifacts.VersionCatalogsExtension

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

val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val baseVersion = versionCatalog.findVersion("sdkVersionName").get().requiredVersion

// CI can override version with -Pversion=X.Y.Z-dev.42+abc123
// Local builds get -local suffix with commit SHA
val versionOverride = project.findProperty("version") as String?
val computedVersion = when {
    versionOverride != null && versionOverride != "unspecified" -> versionOverride
    else -> {
        val gitSha = providers.exec {
            commandLine("git", "rev-parse", "--short=7", "HEAD")
        }.standardOutput.asText.get().trim()
        "$baseVersion-local+$gitSha"
    }
}

allprojects {
    version = computedVersion
}

println("========================================")
println("CloudX SDK Version: $computedVersion")
println("========================================")

true // Needed to make the Suppress annotation work for the plugins block