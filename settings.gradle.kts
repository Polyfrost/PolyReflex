pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.6"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        versions("1.21.11", "26.1.2", "26.2", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "polyreflex"
