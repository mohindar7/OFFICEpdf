import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // PDFBox
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
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