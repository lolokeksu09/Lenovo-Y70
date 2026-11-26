plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.ramwiper.next"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.ramwiper.next"
        minSdk = 26
        targetSdk = 35
        versionCode = 202
        versionName = "2.0.0-alpha.3"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

    }
    buildFeatures { compose = true; aidl = true }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/rustJniLibs"))

    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}

val rustJni = tasks.register<Exec>("buildRustJni") {
    val output = layout.buildDirectory.dir("generated/rustJniLibs")
    inputs.dir(rootProject.file("native/src"))
    inputs.file(rootProject.file("native/Cargo.toml"))
    inputs.file(rootProject.file("tools/build-rust.sh"))
    outputs.dir(output)
    commandLine("bash", rootProject.file("tools/build-rust.sh").absolutePath, output.get().asFile.absolutePath)
}
tasks.named("preBuild").configure { dependsOn(rustJni) }
