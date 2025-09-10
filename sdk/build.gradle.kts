plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.ksp)
    jacoco
}

mavenPublishing {
    // Use the new Central Publisher Portal (S01)
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
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
        debug {
            enableAndroidTestCoverage = true
            enableUnitTestCoverage = true
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

        // Tests are run in isolation if this is set but are slower. Comment if you want test to go faster but less precise
        execution = "ANDROIDX_TEST_ORCHESTRATOR"

        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(files("libs/XorEnc-1.0.1-obf.jar"))
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.android)
    implementation(libs.appcompat)
    implementation(libs.google.advertisingid)
    implementation(libs.google.location)

    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.runtime)

    // Room.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    annotationProcessor(libs.androidx.room.compiler)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.bundles.test.unit)
    androidTestImplementation(libs.bundles.test.instrumentation)
}

tasks.withType(Test::class) {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.*", "sun.*")
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Register a JacocoReport task for code coverage analysis
tasks.register<JacocoReport>("jacocoDebugCodeCoverage") {
    val exclusions = listOf(
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/Logger*.class",
    )

    val unitTests = "testDebugUnitTest"
    // TODO: add Android Tests to coverage measurements
    // val androidTests = "connectedDebugAndroidTest"
    // dependsOn(listOf(unitTests, androidTests))
    dependsOn(listOf(unitTests))
    group = "Reporting"
    description = "Execute unit tests, generate and combine Jacoco coverage report"
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    sourceDirectories.setFrom(layout.projectDirectory.dir("src/main/java"))
    classDirectories.setFrom(
        files(
        fileTree(layout.buildDirectory.dir("intermediates/javac")) {
            exclude(exclusions)
        },
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            exclude(exclusions)
        }
    ))
    executionData.setFrom(
        files(
        fileTree(layout.buildDirectory) { include(listOf("**/*.exec", "**/*.ec")) }
    ))
}
