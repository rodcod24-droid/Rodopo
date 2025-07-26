plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = 34
    namespace = "com.lagradost"
    
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
    
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("com.github.recloudstream:cloudstream:pre-release")
}

// Custom task to create CS3 file
tasks.register<Copy>("makeCS3") {
    dependsOn("assembleRelease")
    
    from("$buildDir/outputs/apk/release") {
        include("*.apk")
        rename { "CuevanaProvider.cs3" }
    }
    into("$rootDir/outputs")
    
    doFirst {
        file("$rootDir/outputs").mkdirs()
    }
}
