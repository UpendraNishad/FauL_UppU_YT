plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    // ADD THIS LINE
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.faul_uppu_yt"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.faul_uppu_yt"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.google.android.material:material:1.12.0")

    // ADD THESE LINES FOR FIREBASE
    // Import the Firebase BoM (Bill of Materials) - this helps manage library versions
    implementation(platform("com.google.firebase:firebase-bom:32.3.1"))
    // Add the dependency for the Firestore database
    implementation("com.google.firebase:firebase-firestore-ktx")
}
