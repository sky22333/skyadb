plugins { id("com.android.library") }

android {
    namespace = "io.github.rhythmcache.fastboot"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies { implementation(libs.kotlinx.coroutines.android) }
dependencies { testImplementation(libs.junit) }
