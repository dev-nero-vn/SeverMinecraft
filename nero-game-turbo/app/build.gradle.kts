plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.nero.gameturbo"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.nero.gamehud"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
