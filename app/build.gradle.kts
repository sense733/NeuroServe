plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.neuroserve"
    compileSdk = libs.versions.compileSdk.get().toInt()
    // buildToolsVersion = "35.0.0" // Let AGP decide

    defaultConfig {
        applicationId = "com.neuroserve"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // ─────────────────────────────────────────────────────────────
    // AndroidX Core
    // ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.datastore.preferences)

    // ─────────────────────────────────────────────────────────────
    // Compose UI (Material3 1.4.0)
    // ─────────────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.bundles.compose.debug)

    // ─────────────────────────────────────────────────────────────
    // Kotlin Coroutines
    // ─────────────────────────────────────────────────────────────
    implementation(libs.bundles.coroutines)

    // ─────────────────────────────────────────────────────────────
    // Hilt Dependency Injection (2.52)
    // ─────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ─────────────────────────────────────────────────────────────
    // Ktor Server (3.4.0) - HTTP API
    // ─────────────────────────────────────────────────────────────
    implementation(libs.bundles.ktor.server)

    // ─────────────────────────────────────────────────────────────
    // Nexa SDK (GGUF + NPU)
    // ─────────────────────────────────────────────────────────────
    implementation("ai.nexa:core:0.0.22")

    // ─────────────────────────────────────────────────────────────
    // Google LiteRT-LM (on-device LLM inference)
    // ─────────────────────────────────────────────────────────────
    implementation(libs.litertlm.android)


    // ─────────────────────────────────────────────────────────────
    // Kotlinx Serialization
    // ─────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ─────────────────────────────────────────────────────────────
    // Testing
    // ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
