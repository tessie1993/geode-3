import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("geode.kotlin-common")
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystoreProps =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }

fun releaseSecret(
    propKey: String,
    envKey: String,
): String? = keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val releaseStorePath = releaseSecret("storeFile", "GEODE_KEYSTORE")
val releaseStorePassword = releaseSecret("storePassword", "GEODE_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSecret("keyAlias", "GEODE_KEY_ALIAS")
val releaseKeyPassword = releaseSecret("keyPassword", "GEODE_KEY_PASSWORD")
val hasReleaseSigning =
    !releaseStorePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

if (System.getenv("GEODE_REQUIRE_RELEASE_SIGNING") == "true") {
    check(hasReleaseSigning) { "Release signing requires all four GEODE signing values." }
    check(file(releaseStorePath!!).isFile) { "Release signing keystore does not exist." }
}

android {
    namespace = "dev.geode"
    // 37 is the floor the Compose 1.12 / lifecycle 2.11 / hilt-navigation 1.4
    // AARs declare; targetSdk stays where it is.
    compileSdk = 37
    // r30 is the 2026 LTS. r28 was the first NDK to align shared objects to 16 KB pages.
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "dev.geode"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "1.8.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            // A debug build installs ALONGSIDE a release one instead of replacing it.
            // Safe to suffix because nothing hardcodes the id: the only thing keyed on
            // it is the FileProvider authority, declared as ${applicationId}.presets,
            // which follows the suffix on its own. The geode:// deep link is scheme
            // based rather than id based, so both builds register it and Android shows
            // a chooser when both are installed - the cost of having both at once.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
    }

    // Builds libgeode.so and libprojectM-4.so from the root CMakeLists.txt.
    externalNativeBuild {
        cmake {
            path = file("../CMakeLists.txt")
            version = "4.1.2"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        checkReleaseBuilds = true
        abortOnError = true
        fatal += "HardcodedText"
    }

    testOptions {
        unitTests {
            // Give unit tests the real resource table rather than a stub, so a test
            // may read a string or an xml the code under test reaches for.
            isIncludeAndroidResources = true
            // An android.* call that nothing has mocked returns a zero/null default
            // instead of throwing "not mocked". Without this, touching an Android
            // type in passing forces an otherwise pure test onto a device.
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val checkNativePageAlignment =
    tasks.register<Exec>("checkNativePageAlignment") {
        description = "Checks every native ELF segment and uncompressed APK entry for 16 KB alignment."
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            rootProject.file("tools/release/check_native_alignment.py").absolutePath,
            layout.buildDirectory.dir("outputs").get().asFile.absolutePath,
        )
    }

listOf("assembleRelease", "bundleRelease").forEach { name ->
    tasks.matching { it.name == name }.configureEach { finalizedBy(checkNativePageAlignment) }
}

dependencies {
    // What the removed :engine:runtime aggregator re-exported to :app via api(...).
    implementation(project(":engine:scenes"))
    implementation(project(":engine:audio-android"))

    implementation(libs.core.splashscreen)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.documentfile)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.glance.appwidget)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
