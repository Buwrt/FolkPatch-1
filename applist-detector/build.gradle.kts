plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.kotlin)
}

android {
    namespace = "icu.nullptr.applistdetector"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    buildFeatures {
        prefab = true
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 36

        externalNativeBuild.ndkBuild {
            arguments += "-j${Runtime.getRuntime().availableProcessors()}"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    externalNativeBuild.ndkBuild {
        path("src/main/cpp/Android.mk")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation("com.android.tools.build:apkzlib:8.11.1")
    implementation("io.github.vvb2060.ndk:xposeddetector:2.2")
}
