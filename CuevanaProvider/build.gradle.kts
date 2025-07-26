plugins {
    id("com.android.library")
    id("kotlin-android")
    id("com.recloudstream.gradle")
}

version = 1

cloudstream {
    language = "es"
    description = "Cuevana streaming provider for Spanish content"
    authors = listOf("YourName")
    status = 1
    tvTypes = listOf("TvSeries", "Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=wv5n.cuevana.biz&sz=%size%"
    requiresResources = false
}

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("com.github.recloudstream:cloudstream:pre-release")
}
