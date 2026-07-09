plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.smilesphere"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smilesphere"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // This sets Java to version 11
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        compose = false
    }
    packaging {
        resources {
            excludes += "**/libarcore_sdk_c.so"
            excludes += "**/libarcore_sdk_jni.so"
        }
    }
}

dependencies {
    // 1. Import the Firebase BoM (This is the "manager")
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))

    // 2. Add Firebase products WITHOUT version numbers
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // Other standard dependencies
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ARCore
    implementation("com.google.ar:core:1.40.0")

    // AR + 3D model rendering - SCENEVIEW ONLY (remove Sceneform)
    implementation("io.github.sceneview:arsceneview:2.2.1")

    // IMPORTANT: Exclude old filament dependencies that cause conflicts
    implementation("io.github.sceneview:sceneview:2.2.1") {
        exclude(group = "com.google.android.filament", module = "filament-android")
        exclude(group = "com.google.ar.sceneform")
    }

    //MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
