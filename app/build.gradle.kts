plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nero.sdstorage"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nero.sdstorage"
        minSdk = 30
        targetSdk = 36
        versionCode = providers.gradleProperty("VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").orElse("1.0.0").get()
    }

    signingConfigs {
        create("release") {
            val keyPath = System.getenv("KEYSTORE_PATH")
            storeFile = if (!keyPath.isNullOrBlank()) file(keyPath) else file("$rootDir/sdmicrotool-release.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "sdmicrotool"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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

dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core:1.15.0")
}
