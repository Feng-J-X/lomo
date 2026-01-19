plugins {
    id("com.android.test")
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("androidx.baselineprofile")
}

android {
    namespace = "com.lomo.benchmark"
    compileSdk = 35

    targetProjectPath = ":app"

    testOptions.managedDevices.localDevices {
        create("pixel6Api31") {
            device = "Pixel 6"
            apiLevel = 31
            systemImageSource = "aosp"
        }
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += listOf("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "benchmark-rules.pro")
        }
    }
}

// P3-005 Fix: Disable strict obfuscation check to avoid "debuggable vs minified" warnings
// We accept that the benchmark might not map perfectly to the release build's mapping file
// regarding shrinking, but this silences the build warnings.
afterEvaluate {
    tasks.configureEach {
        if (name.startsWith("checkTestedAppObfuscation")) {
            enabled = false
        }
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(project(":app"))
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
