import java.util.Properties
import java.io.FileInputStream

plugins {
    id("app-conventions")
    // TODO. Move to toml if possible; getting gradle error if done toml way.
    id("kotlin-parcelize")
}

android {
    namespace = "io.cloudx.demo.demoapp"

    defaultConfig {
        applicationId = namespace
        versionCode = 6
        versionName = libs.versions.sdkVersionName.get()
    }

    // Load keystore properties from file (gitignored)
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file("${rootProject.projectDir}/${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = "cloudx-demo-$name-$versionName.apk"
        }
    }

    packagingOptions {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE",
                "META-INF/LICENSE"
            )
        }
    }
}

dependencies {
    // remote dependencies
//    implementation("io.cloudx:sdk:0.0.1.42")
//    implementation("io.cloudx:adapter-cloudx:0.0.1.42")
//    implementation("io.cloudx:adapter-meta:0.0.1.42")

    // local dev
    implementation(project(":adapter-meta"))
    implementation(project(":adapter-cloudx"))
    implementation(project(":sdk"))

    implementation(libs.core.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.recyclerview)
    implementation(libs.datastore)
    implementation(libs.preferences)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.android)
}