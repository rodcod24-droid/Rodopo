buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        // Try with a specific commit or tag instead of master-SNAPSHOT
        classpath("com.github.recloudstream:gradle:latest-SNAPSHOT")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}
