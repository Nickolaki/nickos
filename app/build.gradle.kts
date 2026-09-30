import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}

android {
    namespace = "com.nbaltas.nickos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nbaltas.nickos"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // Public NickOS update track on nickflix-config (see its nickos.js). Overridable for a
        // friend's own server via local.properties.
        buildConfigField("String", "UPDATE_BASE", "\"${localProperties.getProperty("nickos.updateBase", "https://nickflix-config.nbaltas.org")}\"")
    }

    signingConfigs {
        // Own identity, separate from the Nickflix keys. Every future release must be signed with
        // the same file or Android refuses the update. Back it up outside this box.
        create("release") {
            storeFile = localProperties.getProperty("release.storeFile")?.let { file(it) }
            storePassword = localProperties.getProperty("release.storePassword", "")
            keyAlias = localProperties.getProperty("release.keyAlias", "")
            keyPassword = localProperties.getProperty("release.keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Lucide: the icon set the design handoff was drawn with.
    implementation("com.composables:icons-lucide:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
}
