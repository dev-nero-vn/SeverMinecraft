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
rootProject.name = "NeroRemote"
include(":shared", ":controller", ":host")
project(":shared").projectDir = file("apps/shared")
project(":controller").projectDir = file("apps/controller")
project(":host").projectDir = file("apps/host")
