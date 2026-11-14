plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "iq.turuq.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "API_BASE", "\"${project.findProperty("apiBase") ?: ""}\"")   // فارغ = الوضع التجريبي بالخدمات العامة
        buildConfigField("String", "MAPS_BASE", "\"${project.findProperty("mapsBase") ?: ""}\"")
        buildConfigField("String", "TILES_ONLINE", "\"https://tiles.openfreemap.org/planet\"")   // خريطة العالم المجانية (TileJSON)
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    api(libs.maplibre)
}
