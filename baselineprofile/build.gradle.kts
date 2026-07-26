plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.mj.yata.baselineprofile"
    compileSdk = 35

    defaultConfig {
        // Baseline profile generation needs API 28+; the app itself still ships minSdk 26 and
        // benefits from the generated profile on every API level that supports ART profiles.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // One profile for the whole app rather than per-variant — there's a single shipping variant.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
