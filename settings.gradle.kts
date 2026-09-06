pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

// Force AndroidX in Gradle StartParameter to bypass any stale Gradle daemon cache
gradle.startParameter.projectProperties["android.useAndroidX"] = "true"

gradle.beforeProject {
    extensions.extraProperties["android.useAndroidX"] = "true"
}

rootProject.name = "Baoji"
include(":app")
