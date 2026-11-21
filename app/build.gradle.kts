import java.io.File
import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gatewayUrl = providers.gradleProperty("BOZER_GATEWAY_URL")
    .orElse(providers.environmentVariable("BOZER_GATEWAY_URL"))
    .getOrElse("https://bozerai.bozerpdg.workers.dev")

// The upstream Apache-2.0 Sherpa-ONNX Android AAR is fetched by Gradle and
// pinned by SHA-256; it is not copied into the Git repository.
// The Qwen GGUF is fetched at build time and embedded as an APK asset.
val bozerBrainAssetDir = layout.buildDirectory.dir("generated/bozerBrainAssets")
val bozerBrainAsset = bozerBrainAssetDir.map { it.file("bozer/qwen2.5-0.5b-instruct-q4_k_m.gguf") }
val bozerBrainUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/12145bd1d629190a4d44254073650877954d02c9/qwen2.5-0.5b-instruct-q4_k_m.gguf"
val bozerBrainSha256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
val bozerBrainBytes = 491_400_032L
val downloadBozerBrain = tasks.register("downloadBozerBrain") {
    val output = bozerBrainAsset.get().asFile
    outputs.file(output)
    doLast {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        output.parentFile.mkdirs()
        if (output.isFile && output.length() == bozerBrainBytes && sha256(output) == bozerBrainSha256) return@doLast
        val partial = File(output.parentFile, output.name + ".part")
        partial.delete()
        val connection = URL(bozerBrainUrl).openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 180_000
        }
        try {
            connection.getInputStream().use { input ->
                partial.outputStream().buffered().use { destination -> input.copyTo(destination) }
            }
            check(partial.length() == bozerBrainBytes) { "BOZER brain model size mismatch: ${partial.length()} bytes." }
            check(sha256(partial) == bozerBrainSha256) { "BOZER brain model SHA-256 mismatch." }
            if (output.exists()) output.delete()
            check(partial.renameTo(output)) { "Cannot place bundled BOZER brain asset." }
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }
}

val sherpaAar = layout.buildDirectory.file("third_party/sherpa-onnx-2.0.1.aar")
val sherpaAarUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar"
val sherpaAarSha256 = "a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7"

val downloadSherpaOnnxAar = tasks.register("downloadSherpaOnnxAar") {
    val output = sherpaAar.get().asFile
    outputs.file(output)
    doLast {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        output.parentFile.mkdirs()
        if (output.isFile && sha256(output) == sherpaAarSha256) return@doLast

        val partial = File(output.parentFile, output.name + ".part")
        partial.delete()
        val connection = URL(sherpaAarUrl).openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 180_000
        }
        connection.getInputStream().use { input ->
            partial.outputStream().buffered().use { destination -> input.copyTo(destination) }
        }
        check(sha256(partial) == sherpaAarSha256) {
            "Sherpa-ONNX AAR SHA-256 mismatch; refusing an unverified runtime."
        }
        if (output.exists()) output.delete()
        check(partial.renameTo(output)) { "Cannot install Sherpa-ONNX AAR." }
    }
}

android {
    namespace = "ai.bozer.app"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        buildConfigField("String", "BOZER_GATEWAY_URL", "\"" + gatewayUrl.replace("\"", "\\\"") + "\"")
        applicationId = "ai.bozer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets.getByName("main").assets.srcDir(bozerBrainAssetDir)
    androidResources { noCompress += "gguf" }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(files(sherpaAar))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Use a real JSON implementation in local JVM tests instead of Android stub methods.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

tasks.named("preBuild") {
    dependsOn(downloadSherpaOnnxAar, downloadBozerBrain)
}
tasks.matching { it.name.startsWith("compile") || it.name == "checkDebugAarMetadata" }.configureEach {
    dependsOn(downloadSherpaOnnxAar)
}
