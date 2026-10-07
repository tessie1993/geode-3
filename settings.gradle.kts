pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// Resolves Java toolchains from a public index so a machine that lacks the JDK this build asks
// for downloads it instead of failing. Both the daemon JVM criteria in
// gradle/gradle-daemon-jvm.properties and the compile toolchain in geode.kotlin-common depend on
// this being here: updateDaemonJvm refuses to write its per-platform download URLs without it.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // The upstream 1.1.0 AAR has incompatible RELRO alignment. CI rebuilds
        // its native binaries from pinned, unchanged Apache-2.0 sources.
        // Exclusive resolution fails closed if preparation has not run.
        exclusiveContent {
            forRepository {
                maven {
                    name = "verifiedGraphicsPath"
                    url = uri("build/verified-maven")
                }
            }
            filter {
                includeModule("androidx.graphics", "graphics-path")
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "geode"
include(":app")

include(
    ":engine:audio-core",
    ":engine:scenes",
    ":engine:audio-android",
)
