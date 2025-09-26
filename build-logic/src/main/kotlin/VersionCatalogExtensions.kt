import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun Project.adapterNamespace(adNetworkName: String) =
    libs.findVersionOrThrow("adapterPackageName") + ".${adNetworkName.lowercase()}"

// ../gradle/libs.versions.toml lookup.

internal val VersionCatalog.compileSdk: Int
    get() = findVersionOrThrow("compileSdk").toInt()

internal val VersionCatalog.minSdk: Int
    get() = findVersionOrThrow("minSdk").toInt()

internal val VersionCatalog.targetSdk: Int
    get() = findVersionOrThrow("targetSdk").toInt()

internal val VersionCatalog.javaVersion: JavaVersion
    get() = JavaVersion.toVersion(findVersionOrThrow("javaVersion"))

internal val VersionCatalog.kotlinJvmTarget: String
    get() = findVersionOrThrow("kotlinJvmTarget")

internal val VersionCatalog.testInstrumentationRunner: String
    get() = findVersionOrThrow("testInstrumentationRunner")

internal val VersionCatalog.sdkVersionName: String
    get() = findVersionOrThrow("sdkVersionName")

internal val VersionCatalog.mavenGroupId: String
    get() = findVersionOrThrow("mavenGroupId")

internal val VersionCatalog.testUnit
    get() = findBundleOrThrow("test-unit")

internal val VersionCatalog.kotlinxCoroutinesAndroid
    get() = findLibraryOrThrow("kotlinx-coroutines-android")

private fun VersionCatalog.findLibraryOrThrow(name: String) =
    findLibrary(name)
        .orElseThrow { NoSuchElementException("Library $name not found in version catalog") }

private fun VersionCatalog.findBundleOrThrow(name: String) =
    findBundle(name)
        .orElseThrow { NoSuchElementException("Bundle $name not found in version catalog") }

private fun VersionCatalog.findVersionOrThrow(name: String) =
    findVersion(name)
        .orElseThrow { NoSuchElementException("Version $name not found in version catalog") }
        .requiredVersion