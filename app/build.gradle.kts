plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.kays.photomagic"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kays.photomagic.premier"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
