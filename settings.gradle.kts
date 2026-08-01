pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DermoAI"

include(":app")
include(":core:common")
include(":core:domain")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":core:camera")
include(":core:ml")
include(":core:analytics-engine")
include(":core:environment")
include(":core:reports")
include(":feature:auth")
include(":feature:home")
include(":feature:scan")
include(":feature:timeline")
include(":feature:skinnmind")
include(":feature:treatment")
include(":feature:wellness")
include(":feature:analytics")
include(":feature:reports")
include(":feature:settings")