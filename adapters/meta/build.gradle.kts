plugins {
    id("com.android.library")
    kotlin("android")
    id("com.vanniktech.maven.publish") version "0.34.0"
}

private val releaseVariant = "release"
private val metaVersion = "6.17.0"

android {
    namespace = "io.cloudx.adapter.meta"

    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro") // TODO

        buildConfigField("String", "AUDIENCE_SDK_VERSION_NAME", "\"$metaVersion\"")
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
        jvmTarget = "1.8"
    }

    // Inlined from setupTestOptions
    testOptions {
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

val useRemoteSdk = project.findProperty("useRemoteSdk") == "true"

dependencies {
    if (useRemoteSdk){
        implementation("io.cloudx:sdk:0.0.1.28")
    } else {
        implementation(project(":sdk"))
    }
    implementation("androidx.annotation:annotation:1.8.0")
    implementation("com.facebook.android:audience-network-sdk:$metaVersion")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("io.cloudx", "adapter-meta", "0.0.1.00") // Or set version from tag if needed

    pom {
        name.set("CloudX Adapter - Meta")
        description.set("An Adapter for the CloudX Android SDK: Meta Implementation")
        inceptionYear.set("2025")
        url.set("https://github.com/cloudx-xenoss/cloudexchange.android.adapter-meta")
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
