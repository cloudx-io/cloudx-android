plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.ksp)
}

mavenPublishing {
    // Use the new Central Publisher Portal (S01)
    publishToMavenCentral(automaticRelease = true)
    // signAllPublications() // Disabled for local development - enable only for releases
    
    // IMPORTANT: Version Resolution for Maven Local Development
    // =========================================================
    // For local development, you MUST publish with an explicit version flag:
    //   ./gradlew clean publishToMavenLocal -Pversion=0.0.1.42-LOCAL -x test --no-build-cache
    //
    // Why?
    // - Without -Pversion flag, Gradle uses the fallback from libs.versions.toml
    // - Sometimes the fallback fails silently and Gradle uses "unspecified" as version
    // - This creates artifacts at ~/.m2/repository/io/cloudx/{module}/unspecified/
    // - Flutter will fail to resolve these "unspecified" versions
    //
    // After publishing, verify correct version with:
    //   ls -lah ~/.m2/repository/io/cloudx/sdk/0.0.1.42-LOCAL/
    //
    // To completely reset Maven Local:
    //   rm -rf ~/.m2/repository/io/cloudx
    coordinates(libs.versions.mavenGroupId.get(), "sdk", project.findProperty("version") as String? ?: libs.versions.sdkVersionName.get())

    pom {
        name.set("CloudX SDK")
        description.set("An Android SDK for the CloudX platform")
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

android {
    namespace = libs.versions.sdkPackageName.get()

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = libs.versions.testInstrumentationRunner.get()
        consumerProguardFiles("consumer-rules.pro")

        // Use version from -Pversion property if provided, otherwise use default from catalog
        // This allows: ./gradlew publishToMavenLocal -Pversion=0.0.1.42-LOCAL for local dev
        // And preserves: ./gradlew publish (from CI) to use the proper release version
        val buildVersion = project.findProperty("version") as String? ?: libs.versions.sdkVersionName.get()
        buildConfigField("String", "SDK_VERSION_NAME", "\"$buildVersion\"")
        buildConfigField("long", "SDK_BUILD_TIMESTAMP", "${System.currentTimeMillis()}")

        val configEndpoint = property("cloudx.endpoint.config")
        buildConfigField("String", "CLOUDX_ENDPOINT_CONFIG", """"$configEndpoint"""")
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    testOptions {
        animationsDisabled = true
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("-XX:+EnableDynamicAgentLoading")
            }
        }
    }
}

dependencies {
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.logging)
    implementation(libs.google.advertisingid)
    implementation(libs.lifecycle.process)

    // Room.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.bundles.test.unit)
}
