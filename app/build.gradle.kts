plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.nomi.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nomi.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 132
        versionName = "2.9.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // On-device speech recognition. Only the native runtime ships in the APK - a few MB per
        // ABI - because the model itself is downloaded on first use and would otherwise triple
        // the download size for everyone, including people who never dictate a meal.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DGGML_BUILD_EXAMPLES=OFF",
                    "-DGGML_BUILD_TESTS=OFF",
                    // Android does not provide libomp.so. Linking ggml against it makes the
                    // packaged CPU backend unloadable unless the OpenMP runtime is also shipped.
                    "-DGGML_OPENMP=OFF",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/whisper/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        /**
         * Release builds reuse the local debug key on purpose: the published APKs have always
         * carried that signature, so keeping it lets an optimized build install straight over an
         * existing Nomi without uninstalling and losing the food log.
         */
        create("localRelease") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // A release build exists for what it leaves out: it is not debuggable, so ART
            // optimizes it ahead of time and Compose drops its debug instrumentation, which is
            // what made animations stutter. R8 stays off - nothing here has been verified
            // against shrinking, and proguard-rules.pro is kept ready for when it is.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Deliberately not `findByName(...)?.takeIf { storeFile.exists() }`. That form
            // silently falls back to an UNSIGNED release when the keystore is absent, and an
            // unsigned APK cannot install over the published one - so the build would succeed
            // and the release would fail on someone else's device. require() fails here instead,
            // at configuration time, naming the file it wanted.
            signingConfig = signingConfigs.getByName("localRelease").also {
                require(it.storeFile?.exists() == true) {
                    "Release signing key is missing: ${it.storeFile?.absolutePath}. " +
                        "Refusing to produce an unsigned release. Restore the keystore rather " +
                        "than changing the signing configuration."
                }
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/INDEX.LIST",
            "/META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.androidx.health.connect)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
