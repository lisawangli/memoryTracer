pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "memoryTracer"
include(":app")
include(":hmileak")
include(":hprofanalyzer")
include(":log")
include(":bytehook")

include(":nativeleak")
