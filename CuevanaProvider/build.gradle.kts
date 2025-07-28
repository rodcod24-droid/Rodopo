import com.android.build.gradle.LibraryExtension

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")

configure<LibraryExtension> {
    compileSdk = 34
    namespace = "com.lagradost"

    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// Configure Kotlin options separately
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("com.github.recloudstream:cloudstream:pre-release")
}
