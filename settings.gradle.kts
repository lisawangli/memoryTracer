pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = java.net.URI("https://maven.aliyun.com/repository/public")
        }
        maven {
            credentials {
                username = "66c3fb599a2728f7f2b47f5f"
                password = "qCumIWT]qbRi"
            }
            url  = java.net.URI("https://packages.aliyun.com/66c3fc94f168c2ed4c91bd2a/maven/repo-vnunv")
        }
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