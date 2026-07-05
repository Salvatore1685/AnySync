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
        // Repository JitPack: necessario per la libreria sardine-android (client WebDAV)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RouterSync"
include(":app")
