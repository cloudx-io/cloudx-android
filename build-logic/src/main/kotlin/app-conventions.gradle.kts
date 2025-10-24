plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = libs.compileSdk

    defaultConfig {
        minSdk = libs.minSdk
        targetSdk = libs.targetSdk
        testInstrumentationRunner = libs.testInstrumentationRunner
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    setupCompileOptions(libs)
    setupKotlinJvmOptions(libs)
    setupTestOptions()
}

dependencies {
    testImplementation(libs.testUnit)
}