plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.criandroidsimple"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.criandroidsimple3"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "3.3"
    }

    // VOEG DIT BLOK TOE OM DE APK-NAAM AAN TE PASSEN
    applicationVariants.all {
        outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (output != null) {
                // Pas hier het gewenste formaat aan:
                val newName = "CRIAndroidSimple-${buildType.name}-v${defaultConfig.versionName}.apk"
                output.outputFileName = newName
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}


