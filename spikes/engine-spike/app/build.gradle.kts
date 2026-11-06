// Prueba técnica de la Etapa 0: valida youtubedl-android (Python + yt-dlp + QuickJS + FFmpeg)
// en un dispositivo real o emulador. No es código de producción.
plugins {
    id("com.android.application")
}

android {
    namespace = "dev.mediaforge.spike"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.mediaforge.spike"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    // Los binarios (libpython.so, libffmpeg.so, libqjs.so) se ejecutan desde nativeLibraryDir:
    // tienen que extraerse al instalar.
    packaging { jniLibs { useLegacyPackaging = true } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("junit:junit:4.13.2")
}
