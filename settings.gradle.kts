pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven(url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea"))
    }
}

rootProject.name = "cloudx-sdk"
include(":app")

// local dev
include(":adapter-cloudx")
include(":adapter-meta")
include(":sdk")