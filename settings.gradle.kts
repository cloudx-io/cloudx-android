pluginManagement {
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

        // GitHub Packages for internal builds
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/cloudx-io/cloudx-android")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "cloudx-sdk"
include(":app")

// local dev
include(":adapter-cloudx")
include(":adapter-meta")
include(":sdk")