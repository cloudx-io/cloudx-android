plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.mavenPublish)
}

android {
    namespace = "io.cloudx.adapter.meta"

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = libs.versions.testInstrumentationRunner.get()
        consumerProguardFiles("consumer-rules.pro") // TODO

        buildConfigField("String", "AUDIENCE_SDK_VERSION_NAME", "\"${libs.versions.metaAudienceNetworkVersion.get()}\"")
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

    // Inlined from setupCompileOptions
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Inlined from setupKotlinJvmOptions
    kotlinOptions {
        jvmTarget = libs.versions.kotlinJvmTarget.get()
    }

    // Inlined from setupTestOptions
    testOptions {
        animationsDisabled = true
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":sdk"))
    implementation(libs.androidx.annotation)
    implementation(libs.metaAudienceNetwork)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    // signAllPublications() // Disabled for local development - enable only for releases
    coordinates(libs.versions.mavenGroupId.get(), "adapter-meta", project.findProperty("version") as String? ?: "0.0.1.00")

    pom {
        name.set("CloudX Adapter - Meta")
        description.set("An Adapter for the CloudX Android SDK: Meta Implementation")
        inceptionYear.set("2025")
        url.set("https://github.com/cloudx-xenoss/cloudexchange.android.sdk")
        licenses {
            license {
                name.set("Elastic License 2.0")
                url.set("https://www.elastic.co/licensing/elastic-license")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("CloudX")
                name.set("CloudX Team")
                url.set("https://cloudx.io")
            }
        }
        scm {
            url.set("https://github.com/cloudx-xenoss/cloudexchange.android.sdk")
            connection.set("scm:git:git://github.com/cloudx-xenoss/cloudexchange.android.sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/cloudx-xenoss/cloudexchange.android.sdk.git")
        }
    }
}
