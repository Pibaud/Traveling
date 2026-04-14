plugins {
    alias(libs.plugins.android.application)
    kotlin("plugin.serialization")
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.application"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.application"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val maptilerKey = project.findProperty("MAPTILER_API_KEY")?.toString() ?: ""
        buildConfigField("String", "MAPTILER_API_KEY", maptilerKey)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")
    implementation("io.coil-kt:coil:2.6.0")
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("org.maplibre.gl:android-sdk:10.0.2")
    // Retrofit pour les appels réseau
    implementation("com.squareup.retrofit2:retrofit:2.9.0")

    // Convertisseur pour que Retrofit comprenne la sérialisation Kotlin
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Client HTTP (optionnel mais recommandé pour les logs)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Pour l'intégration du ViewModel et du cycle de vie
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation(libs.androidx.navigation.fragment)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}