plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val probeClasses = layout.buildDirectory.dir("probe/classes")
val probeAssets = layout.buildDirectory.dir("generated/probeAssets")
val compileAppProbe = tasks.register<JavaCompile>("compileAppProbe") {
    source = fileTree("src/probe/java") { include("*.java") }
    classpath = files(androidComponents.sdkComponents.bootClasspath)
    destinationDirectory = probeClasses
    sourceCompatibility = "8"
    targetCompatibility = "8"
    options.encoding = "UTF-8"
}
val dexAppProbe = tasks.register<JavaExec>("dexAppProbe") {
    dependsOn(compileAppProbe)
    val output = probeAssets.map { it.dir("probe") }
    inputs.dir(probeClasses)
    outputs.dir(output)
    classpath = files(androidComponents.sdkComponents.sdkDirectory.map { it.file("build-tools/${android.buildToolsVersion}/lib/d8.jar") })
    mainClass = "com.android.tools.r8.D8"
    doFirst {
        output.get().asFile.mkdirs()
        args = listOf("--min-api", "21", "--output", output.get().asFile.absolutePath,
            probeClasses.get().file("SkyAppsProbe.class").asFile.absolutePath)
    }
}
android.sourceSets.getByName("main").assets.directories.add(probeAssets.get().asFile.absolutePath)
tasks.named("preBuild").configure { dependsOn(dexAppProbe) }

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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isShrinkResources = true
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            // Bouncy Castle's Java multi-release OSGi descriptors are not Android runtime resources.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            merges += "META-INF/LICENSE.md"
        }
    }
}

dependencies {
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.koog.openai)
    implementation(libs.koog.ktor)
    implementation(libs.koog.mcp) {
        exclude(group = "ai.koog", module = "prompt-executor-llms-all")
        exclude(group = "ai.koog", module = "prompt-executor-llms-all-android")
        exclude(group = "ai.koog", module = "serialization-jackson")
    }
    implementation(libs.ktor.okhttp)
    implementation(libs.enriched.markdown)
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kadb)
    implementation(libs.kadb.mdns)
    implementation(libs.fastboot.java)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.timber)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
