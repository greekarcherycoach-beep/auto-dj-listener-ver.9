plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Η υπογραφή release διαβάζεται από μεταβλητές περιβάλλοντος (το GitHub Actions τις δίνει από τα Secrets).
// Αν λείπουν, το release APK μένει ανυπόγραφο και χρησιμοποιείται το debug APK.
val ksFile: String? = System.getenv("KEYSTORE_FILE")
val ksPass: String? = System.getenv("KEYSTORE_PASSWORD")
val ksAlias: String? = System.getenv("KEY_ALIAS")
val ksKeyPass: String? = System.getenv("KEY_PASSWORD")
val canSign = !ksFile.isNullOrBlank() && file(ksFile).exists() && !ksPass.isNullOrBlank() && !ksAlias.isNullOrBlank()

android {
    namespace = "com.djape1.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.djape1.player"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        if (canSign) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = if (ksKeyPass.isNullOrBlank()) ksPass else ksKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSign) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    // Σάρωση QR κωδικού πρόσκλησης
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
