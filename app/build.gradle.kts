import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Screenshot tests (Robolectric) to check screens against design/screens/*.png off-device.
    id("io.github.takahirom.roborazzi")
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
        versionCode = 4
        versionName = "0.4"

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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// The ordinary test run COMPARES every screen with its saved picture in design/screens (see
// DesignScreensTest). `:app:recordRoborazziDebug` is the one command that rewrites those pictures, so
// verify mode is on for every test run except when someone asked for a roborazzi task themselves.
tasks.withType<Test>().configureEach {
    val asked = gradle.startParameter.taskNames.joinToString(" ").lowercase()
    val ownMode = "roborazzi" in asked || project.hasProperty("roborazzi.test.record") ||
        project.hasProperty("roborazzi.test.compare") || project.hasProperty("roborazzi.test.verify")
    if (!ownMode) systemProperty("roborazzi.test.verify", "true")
    // Print the failure message (it names the screen and the re-record command) in the console.
    testLogging { exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
    // The saved pictures are what the tests compare against, so editing one must re-run them
    // (without this Gradle calls the test task up to date and never looks at a changed picture).
    inputs.dir(rootProject.file("design/screens")).withPropertyName("designScreens").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Cameras: Tapo/go2rtc RTSP feeds (and plain HLS/HTTP video) on the Master.
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.9.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.0")
    // Watch together: PlayerView for the Node's synced player.
    implementation("androidx.media3:media3-ui:1.9.0")
    // Watch together: one tablet serves a movie file to the others over the home WiFi.
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    // Tablet cameras: a Node's front camera, frame by frame, for the Master's Cameras screen.
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    // QR code for zero-adb kid tablet setup.
    implementation("com.google.zxing:core:3.5.3")
    // Lucide: the icon set the design handoff was drawn with.
    implementation("com.composables:icons-lucide:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2026.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
