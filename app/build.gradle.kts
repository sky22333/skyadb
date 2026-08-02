plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sky22333.skyadb"
    compileSdk = 37
    val ciVersionCode = providers.environmentVariable("VERSION_CODE").map { it.toInt() }.orNull
    val ciVersionName = providers.environmentVariable("VERSION_NAME").orNull
    val signingStoreFile = providers.environmentVariable("SIGNING_STORE_FILE").orNull
    val keyAliasValue = providers.environmentVariable("KEY_ALIAS").orNull
    val keyStorePasswordValue = providers.environmentVariable("KEY_STORE_PASSWORD").orNull
    val keyPasswordValue = providers.environmentVariable("KEY_PASSWORD").orNull
    val releaseSigningEnabled = !signingStoreFile.isNullOrBlank() &&
        !keyAliasValue.isNullOrBlank() &&
        !keyStorePasswordValue.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank()

    defaultConfig {
        applicationId = "com.sky22333.skyadb"
        minSdk = 24
        targetSdk = 37
        versionCode = ciVersionCode ?: 10000
        versionName = ciVersionName ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningEnabled) {
                storeFile = file(signingStoreFile!!)
                storeType = "PKCS12"
                keyAlias = keyAliasValue!!
                storePassword = keyStorePasswordValue!!
                keyPassword = keyPasswordValue!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kadb)
    implementation(libs.fastboot.java)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.timber)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
