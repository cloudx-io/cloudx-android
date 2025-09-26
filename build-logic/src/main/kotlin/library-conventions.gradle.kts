import gradle.kotlin.dsl.accessors._624aae704a5c30b505ab3598db099943.android
import gradle.kotlin.dsl.accessors._624aae704a5c30b505ab3598db099943.testImplementation

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = libs.compileSdk

    defaultConfig {
        minSdk = libs.minSdk
        testInstrumentationRunner = libs.testInstrumentationRunner
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets["main"].kotlin {
        srcDir("src/main/samples/kotlin")
    }

    setupCompileOptions(libs)
    setupKotlinJvmOptions(libs)
    setupTestOptions()
}

dependencies {
    testImplementation(libs.testUnit)
}