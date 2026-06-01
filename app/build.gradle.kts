import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()
    
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
            binaries.executable()
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
        }
        
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.appcompat)
            implementation(libs.material)
            
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            
            // PDFBox Android-specific
            implementation(libs.pdfbox.android)
        }
        
        wasmJsMain.dependencies {
        }
    }
}

android {
    namespace = "com.example.officepdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.officepdf"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
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
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { stream -> versionProps.load(stream) }
}
val buildNumber: Int = versionProps.getProperty("VERSION_CODE", "1").toIntOrNull() ?: 1

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("officepdf_${buildNumber}.apk")
        }
    }
}

tasks.register("incrementVersionCode") {
    doLast {
        val nextCode = buildNumber + 1
        versionProps.setProperty("VERSION_CODE", nextCode.toString())
        versionPropsFile.outputStream().use { out -> versionProps.store(out, null) }
    }
}
tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy("incrementVersionCode")
}