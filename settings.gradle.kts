pluginManagement {
    includeBuild(
        providers.gradleProperty("androidBuildLogicDir")
            .getOrElse("build-logic")
    )
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

rootProject.name = "Lector"
include(":app")
