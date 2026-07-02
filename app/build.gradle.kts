import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.arihant.streaks"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arihant.streaks"
        minSdk = 31
        targetSdk = 35
        versionCode = 6
        versionName = "2.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProps["KEYSTORE_PATH"] as? String ?: "../my-release-key.jks")
            storePassword = localProps["KEYSTORE_PASSWORD"] as? String
            keyAlias = localProps["KEY_ALIAS"] as? String
            keyPassword = localProps["KEY_PASSWORD"] as? String
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.datastore:datastore-preferences:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.transition)
    implementation("com.google.android.material:material:1.11.0")
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")
}