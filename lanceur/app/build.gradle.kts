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
        versionCode = 16
        versionName = "2.1"
    }

    // Clé de signature d'Alex Marceau Prevost : fournie par les Secrets GitHub
    // (LEXOS_KEYSTORE_B64 + LEXOS_KEYSTORE_PASS), jamais dans le dépôt public.
    // Sans secret, l'ancienne clé lexos.jks est utilisée.
    signingConfigs {
        create("lexos") {
            val chemin = System.getenv("LEXOS_KEYSTORE_PATH")?.takeIf { it.isNotEmpty() }
            val mdp = System.getenv("LEXOS_KEYSTORE_PASS")?.takeIf { it.isNotEmpty() && chemin != null } ?: "lexospro"
            val ks = file(chemin ?: "lexos.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = mdp
                keyAlias = "lexos"
                keyPassword = mdp
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
