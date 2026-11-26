plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.ramwiper.next.nativeengine"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    ndkVersion = "27.2.12479018"
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("generated/rustJniLibs"))
}
dependencies {
    implementation(project(":domain"))
}
val rustJni = tasks.register<Exec>("buildRustJni") {
    val output = layout.buildDirectory.dir("generated/rustJniLibs")
    inputs.dir(rootProject.file("native/src"))
    inputs.file(rootProject.file("native/Cargo.toml"))
    inputs.file(rootProject.file("rust-toolchain.toml"))
    inputs.file(rootProject.file("tools/build-rust.sh"))
    outputs.dir(output)
    commandLine("bash", rootProject.file("tools/build-rust.sh").absolutePath, output.get().asFile.absolutePath)
}
tasks.named("preBuild").configure { dependsOn(rustJni) }
