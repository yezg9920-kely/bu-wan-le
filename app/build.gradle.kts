plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val releaseStoreFile = providers.environmentVariable("BUWANLE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("BUWANLE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("BUWANLE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("BUWANLE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.focusgate.app"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("community") {
            dimension = "edition"
            applicationId = "io.github.yezg9920kely.buwanle"
            versionCode = 2_000_000
            versionName = "2.0.0-beta"
            buildConfigField("String", "EDITION", "\"community\"")
            buildConfigField("boolean", "CLOUD_ENABLED", "false")
        }
        create("legacyBridge") {
            dimension = "edition"
            applicationId = "com.focusgate.app"
            versionCode = 14
            versionName = "1.4.8"
            buildConfigField("String", "EDITION", "\"legacy-bridge\"")
            buildConfigField("boolean", "CLOUD_ENABLED", "false")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
