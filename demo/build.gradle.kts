plugins {
    id("app-conventions")
    // TODO. Move to toml if possible; getting gradle error if done toml way.
    id("kotlin-parcelize")
}

android {
    namespace = "io.cloudx.demo.demoapp"

    defaultConfig {
        applicationId = namespace
        versionName = libs.versions.sdkVersionName.get()
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
    // publishers
//    implementation("io.cloudx:sdk:0.0.1.36")
//    implementation("io.cloudx:adapter-google:v0.0.1.14")
//    implementation("io.cloudx:adapter-cloudx:0.0.1.00")
//    implementation("io.cloudx:adapter-meta:0.0.1.00")
//    implementation("io.cloudx:adapter-mintegral:0.0.1.00")
//    implementation("io.cloudx:adapter-testbidder:0.0.1.27")

    // local dev
    implementation(project(":adapters:testbidder"))
    implementation(project(":adapters:meta"))
    implementation(project(":adapters:cloudx"))
    implementation(project(":adapters:google"))
    implementation(project(":adapters:mintegral"))
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

    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)

    implementation(libs.kotlinx.coroutines.android)
}