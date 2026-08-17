// Needle ships a *static* libneedle.a with a plain C API, not a prebuilt JNI .so like
// cactus does, so the JNI shim in src/main/cpp is ours. The KMP Android library plugin
// has no NDK support, hence this plain Android library, consumed by :needle.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.needle.nativelib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
