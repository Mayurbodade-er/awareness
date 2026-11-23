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

rootProject.name = "Awareness"

// Modules for VS-01, VS-02, VS-03 & VS-04
include(":app")
include(":core:domain")
include(":core:design-system")
include(":core:presentation")
include(":core:data")
include(":core:accessibility")
include(":core:overlay")
include(":feature:settings")
include(":feature:hud")
