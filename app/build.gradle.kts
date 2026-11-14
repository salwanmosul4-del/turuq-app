plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "iq.turuq.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "iq.turuq.araqna"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0.0"
    }
    // توقيع الإصدار: يُقرأ من متغيرات البيئة التي يضبطها ملف release.yml من أسرار GitHub
    val ksPath = System.getenv("TURUQ_KEYSTORE")
    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("TURUQ_STORE_PASS")
                keyAlias = System.getenv("TURUQ_KEY_ALIAS")
                keyPassword = System.getenv("TURUQ_KEY_PASS")
            }
        }
    }
    buildTypes {
        release {
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false   // مؤقتاً: التصغير قد يكسر MapLibre/serialization دون قواعد حماية مجرَّبة
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":car"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.ktx)
    implementation(libs.car.app.projected)
    implementation(libs.car.app)
}
