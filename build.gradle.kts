apply plugin: 'com.android.library'
apply plugin: 'kotlin-android'

android {
    compileSdk 34
    namespace 'com.lagradost'

    defaultConfig {
        minSdk 21
        targetSdk 34
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = '1.8'
    }
}

dependencies {
    implementation 'com.github.recloudstream:cloudstream:pre-release'
}
