plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "pro.magisk"
    compileSdk {
        version = release(34)
    }

    defaultConfig {
        applicationId = "pro.magisk"
        minSdk = 24
        targetSdk = 34
        versionCode = 67678
        versionName = "mega"

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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
}