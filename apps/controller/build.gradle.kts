plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "vn.nero.remote.controller"
    compileSdk = 36
    defaultConfig {
        applicationId = "vn.nero.remote.controller"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"https://geacjfwatxmfzezkholp.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_NrxIoKwjymmt8fRLKUfe0A_f7ma0EA5\"")
    }
    buildFeatures { buildConfig = true }
}
dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
}
