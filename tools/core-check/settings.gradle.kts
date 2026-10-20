// Builds and tests only the :core module, without the Android Gradle Plugin.
// Useful on machines with no Android SDK:  ./gradlew -p tools/core-check :core:test
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo1.maven.org/maven2")
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "core-check"
include(":core")
project(":core").projectDir = file("../../core")
