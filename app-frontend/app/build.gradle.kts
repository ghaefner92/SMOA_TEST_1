import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.imiq"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.imiq"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("localUsb") {
            dimension = "environment"
            applicationIdSuffix = ".local.usb"
            versionNameSuffix = "-local-usb"
            buildConfigField("String", "DYCONET_BASE_URL", "\"http://127.0.0.1:8077\"")
            buildConfigField("boolean", "LOCAL_CORE_MODE", "true")
        }
        create("production") {
            dimension = "environment"
            buildConfigField(
                "String",
                "DYCONET_BASE_URL",
                "\"https://imiq-app.et.uni-magdeburg.de\"",
            )
            buildConfigField("boolean", "LOCAL_CORE_MODE", "false")
        }
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(libs.sceneview)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose BOM - this manages all Compose library versions
    implementation(platform(libs.androidx.compose.bom))

    // Compose dependencies
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Optional but useful for development
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Testing dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Networking - OkHttp
    implementation(libs.okhttp)

    // Serialization - Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    // Retrofit for API calls
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // Google Play Services for location
    implementation(libs.play.services.location)

    // MapLibre — free vector maps (GL) with a dark style + GPU polylines
    implementation(libs.maplibre)
}
