import com.android.build.api.dsl.CommonExtension
import gradle.kotlin.dsl.accessors._624aae704a5c30b505ab3598db099943.kotlinOptions
import gradle.kotlin.dsl.accessors._d3a9078579e8ce6e43c6b12121fa9adc.kotlinOptions
import org.gradle.api.JavaVersion
import org.gradle.api.artifacts.VersionCatalog

internal fun CommonExtension<*, *, *, *, *>.setupCompileOptions(libs: VersionCatalog) {
    compileOptions {
        val jver = JavaVersion.toVersion(libs.javaVersion)
        sourceCompatibility = jver
        targetCompatibility = jver
    }
}

internal fun com.android.build.gradle.LibraryExtension.setupKotlinJvmOptions(libs: VersionCatalog) {
    kotlinOptions {
        jvmTarget = libs.kotlinJvmTarget
    }
}

internal fun com.android.build.gradle.internal.dsl.BaseAppModuleExtension.setupKotlinJvmOptions(libs: VersionCatalog) {
    kotlinOptions {
        jvmTarget = libs.kotlinJvmTarget
    }
}

internal fun CommonExtension<*, *, *, *, *>.setupTestOptions() {
    testOptions {
        animationsDisabled = true
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}