plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "iq.turuq.automotive"
    compileSdk = 36
    defaultConfig {
        applicationId = "iq.turuq.araqna"   // نفس المعرّف: إصدار خاص بـ Automotive OS ضمن التطبيق نفسه في Play
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":car"))
    implementation(libs.car.app.automotive)
    implementation(libs.core.ktx)
}
