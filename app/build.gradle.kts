import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}


// Tambahkan di bagian atas file sebelum blok android { ... }
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
android {
    namespace = "id.ac.stmi.api"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "id.ac.stmi.api"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        val mapboxToken = localProperties.getProperty("MAPBOX_ACCESS_TOKEN") ?: "YOUR_MAPBOX_ACCESS_TOKEN_HERE"
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"$mapboxToken\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    implementation("androidx.health.connect:connect-client:1.2.0-alpha06")

    // Retrofit & Gson (sesuai modul pertemuan 11)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines & LifecycleScope
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("com.google.android.gms:play-services-location:21.3.0")

}