import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("android.release-conventions")
}

android {
    namespace = "io.github.smicca.lector"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.smicca.lector"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

// --- sherpa-onnx native JNI & Kotlin API acquisition ---
val sherpaOnnxVersion = "2.0.2"
val sherpaEngineCacheDir = layout.buildDirectory.dir("sherpaEngineCache").get().asFile
val sherpaGeneratedDir = layout.buildDirectory.dir("generated/sherpaEngine").get().asFile
val sherpaJniLibsDir = File(sherpaGeneratedDir, "jniLibs")
val sherpaKotlinDir = File(sherpaGeneratedDir, "kotlin")
val sherpaAbis = listOf("arm64-v8a", "armeabi-v7a")

fun downloadIfMissing(url: String, target: File) {
    if (target.isFile && target.length() > 0) return
    target.parentFile.mkdirs()
    val tmp = File(target.parentFile, "${target.name}.part")
    URL(url).openStream().use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
    tmp.renameTo(target)
}

val fetchSherpaEngine = tasks.register("fetchSherpaEngine") {
    description = "Downloads sherpa-onnx native libraries and Kotlin OfflineTts API."
    val jniStampFile = File(sherpaJniLibsDir, ".fetched")
    val kotlinTtsFile = File(sherpaKotlinDir, "com/k2fsa/sherpa/onnx/Tts.kt")
    val kotlinWaveFile = File(sherpaKotlinDir, "com/k2fsa/sherpa/onnx/WaveReader.kt")

    outputs.dir(sherpaJniLibsDir)
    outputs.dir(sherpaKotlinDir)

    doLast {
        if (!jniStampFile.isFile) {
            val releaseTarball = File(sherpaEngineCacheDir, "sherpa-onnx-v$sherpaOnnxVersion-android.tar.bz2")
            downloadIfMissing(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaOnnxVersion/" +
                    "sherpa-onnx-v$sherpaOnnxVersion-android.tar.bz2",
                releaseTarball
            )
            val extractedJni = File(sherpaEngineCacheDir, "jniLibsExtracted")
            copy {
                from(tarTree(resources.bzip2(releaseTarball)))
                into(extractedJni)
                include(sherpaAbis.map { abi -> "jniLibs/$abi/*.so" })
            }
            copy {
                from(File(extractedJni, "jniLibs"))
                into(sherpaJniLibsDir)
            }
            jniStampFile.parentFile.mkdirs()
            jniStampFile.writeText("ok")
        }

        if (!kotlinTtsFile.isFile) {
            kotlinTtsFile.parentFile.mkdirs()
            downloadIfMissing(
                "https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v$sherpaOnnxVersion/" +
                    "sherpa-onnx/kotlin-api/Tts.kt",
                kotlinTtsFile
            )
        }
        if (!kotlinWaveFile.isFile) {
            kotlinWaveFile.parentFile.mkdirs()
            downloadIfMissing(
                "https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v$sherpaOnnxVersion/" +
                    "sherpa-onnx/kotlin-api/WaveReader.kt",
                kotlinWaveFile
            )
        }
    }
}

android {
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(sherpaJniLibsDir)
            java.srcDir(sherpaKotlinDir)
        }
    }
}

afterEvaluate {
    tasks.named("preBuild") {
        dependsOn(fetchSherpaEngine)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.apache.commons:commons-compress:1.26.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
