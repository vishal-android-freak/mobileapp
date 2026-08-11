import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
                }
            }
        }
    }

    android {
        namespace = "com.needle"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Deliberately NOT under commonMain/resources: those are packaged into the
    // Android APK, and these are iOS-only build inputs. cinterop just needs a path.
    val iosLibDir = project.file("libs")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        val libSubdir = when (target.name) {
            "iosArm64" -> "ios-arm64"
            else -> "ios-arm64-simulator"
        }
        target.compilations.getByName("main") {
            cinterops {
                create("needle") {
                    defFile("src/nativeInterop/cinterop/needle.def")
                    includeDirs("src/nativeInterop/cinterop")
                    extraOpts("-libraryPath", iosLibDir.resolve(libSubdir).absolutePath)
                }
            }
        }
        target.binaries.all {
            // libneedle.a is C++; without this the iOS link fails on std:: symbols.
            linkerOpts("-lc++")
        }
    }

    sourceSets {
        androidMain.dependencies {
            api(project(":needle-native"))
        }
    }
}
