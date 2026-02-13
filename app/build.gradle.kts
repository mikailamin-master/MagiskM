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
        versionCode = 55555
        versionName = "master"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    signingConfigs {
        create("release") {
            storeFile = file("main_key.jks")
            storePassword = "superman"
            keyAlias = "mikailamin"
            keyPassword = "superman"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
    
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(files("libs/core-6.0.0.aar"))
    implementation(files("libs/service-6.0.0.aar"))
    implementation(files("libs/nio-6.0.0.aar"))
}