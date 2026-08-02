plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

import java.util.Properties

val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.musicdownloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.musicdownloader"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "2.2-night"

        buildConfigField("String", "GENIUS_ACCESS_TOKEN", "\"${secrets.getProperty("GENIUS_ACCESS_TOKEN", "")}\"")
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    implementation("com.google.code.findbugs:jsr305:3.0.2")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-tls:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // ID3 Tagging
    implementation("net.jthink:jaudiotagger:3.0.1")

    // Opus Tagging (Vorbis Comments)
    implementation("org.gagravarr:vorbis-java-core:0.8")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Coil para carátulas
    implementation("io.coil-kt:coil:2.6.0")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Java 8+ API desugaring (para URLDecoder en Android < 33)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
