import gradle.kotlin.dsl.accessors._0ac9a36cec4eeb1254edca678008b431.publishing
import gradle.kotlin.dsl.accessors._624aae704a5c30b505ab3598db099943.android

plugins {
    `maven-publish`
}

private val releaseVariant = "release"

android {
    publishing {
        singleVariant(releaseVariant) {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>(releaseVariant) {
            groupId =  libs.groupId
            // TODO. More robust approach in case project name changes
            artifactId = project.name
            version = libs.sdkVersionName

            afterEvaluate {
                from(components[releaseVariant])
            }
        }
    }
}