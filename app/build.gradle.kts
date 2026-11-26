plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.ramwiper.next"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.ramwiper.next"
        minSdk = 26
        targetSdk = 35
        versionCode = 203
        versionName = "2.0.0-alpha.4"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":native-engine"))
    implementation(project(":presentation"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
