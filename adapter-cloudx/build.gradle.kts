plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.mavenPublish)
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

android {
    namespace = "io.cloudx.adapter.cloudx"

    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = libs.versions.testInstrumentationRunner.get()
        consumerProguardFiles("consumer-rules.pro") // TODO
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
    implementation(libs.appcompat)
    implementation(libs.webkit)
    implementation(libs.customtabs)

    // Test dependencies
    testImplementation(libs.bundles.test.unit)
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)

    // Only sign if GPG keys are configured (required for Maven Central, not needed for GitHub Packages)
    val signingKey = providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey")
    if (signingKey.isPresent) {
        signAllPublications()
    }

    coordinates(libs.versions.mavenGroupId.get(), "adapter-cloudx", project.version.toString())

    pom {
        name.set("CloudX Adapter - CloudX")
        description.set("An Adapter for the CloudX Android SDK: CloudX Implementation")
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
