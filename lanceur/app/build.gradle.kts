plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ca.lexos.lanceur"
    compileSdk = 34

    defaultConfig {
        applicationId = "ca.lexos.lanceur"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Clé de signature : fournie par GitHub (secret LEXOS_KEYSTORE) pour que
    // chaque nouvelle version s'installe par-dessus l'ancienne.
    signingConfigs {
        create("lexos") {
            val ks = file(System.getenv("LEXOS_KEYSTORE_PATH") ?: "lexos.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("LEXOS_KEYSTORE_PASS") ?: "lexospro"
                keyAlias = System.getenv("LEXOS_KEY_ALIAS") ?: "lexos"
                keyPassword = System.getenv("LEXOS_KEYSTORE_PASS") ?: "lexospro"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val cfg = signingConfigs.getByName("lexos")
            signingConfig = if (cfg.storeFile != null) cfg else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
