plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.ksp)
}

// Configure publishing repositories
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cloudx-io/cloudx-android")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

mavenPublishing {
    // Use the new Central Publisher Portal (S01)
    publishToMavenCentral(automaticRelease = true)

    // Only sign if GPG keys are configured (required for Maven Central, not needed for GitHub Packages)
    val signingKey = providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey")
    if (signingKey.isPresent) {
        signAllPublications()
    }

    coordinates(libs.versions.groupId.get(), "sdk", project.version.toString())

    pom {
        name.set("CloudX SDK")
        description.set("An Android SDK for the CloudX platform")
        inceptionYear.set("2025")
        url.set("https://github.com/cloudx-io/cloudx-android")
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
            url.set("https://github.com/cloudx-io/cloudx-android")
            connection.set("scm:git:git://github.com/cloudx-io/cloudx-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/cloudx-io/cloudx-android.git")
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

        buildConfigField("String", "SDK_VERSION_NAME", "\"${libs.versions.sdkVersionName.get()}\"")
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
