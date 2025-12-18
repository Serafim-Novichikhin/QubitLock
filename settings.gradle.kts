pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "QubitLock"

include(":qubitlock-core")
include(":qubitlock-starter-ktor")
include(":qubitlock-app")
include(":example-of-using-qubitlock")