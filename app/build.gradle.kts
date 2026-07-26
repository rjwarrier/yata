import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.baselineprofile)
}

// Release signing — kept out of git entirely (both the keystore itself, elsewhere on disk,
// and this properties file are .gitignore'd). Absent on a machine without it, the release
// build type just falls back to unsigned rather than failing the build.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.mj.yata"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mj.yata"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.8 beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeType = (keystoreProperties["storeType"] as? String) ?: "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    lint {
        // Translation readiness. HardcodedText only catches literals in XML layouts, which this
        // app has none of — the Compose equivalent is caught by the :app:lintHardcodedStrings
        // helper task below. These two do apply: MissingTranslation fires once a non-default
        // locale exists and a key is absent from it, and MissingQuantity catches a <plurals>
        // missing a form some language requires.
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        disable += "HardcodedText"
    }
}

/**
 * Reports Kotlin UI strings still written as literals rather than pulled from strings.xml.
 * Advisory, not a build failure — the migration is incremental, so failing the build would
 * block every unrelated change until all of it is done. Run it to see what's left:
 *
 *   ./gradlew :app:lintHardcodedStrings
 */
tasks.register("lintHardcodedStrings") {
    group = "verification"
    description = "Counts hardcoded UI strings still needing extraction to strings.xml."
    val sourceDir = file("src/main/java")
    doLast {
        val pattern = Regex("""(Text\(\s*"[^"]{2,}"|contentDescription\s*=\s*"[^"]{2,}")""")
        var total = 0
        val perFile = sortedMapOf<String, Int>()
        sourceDir.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val n = pattern.findAll(f.readText()).count()
            if (n > 0) {
                perFile[f.relativeTo(sourceDir).path] = n
                total += n
            }
        }
        if (total == 0) {
            logger.lifecycle("No hardcoded UI strings found — extraction complete.")
        } else {
            logger.lifecycle("$total hardcoded UI string(s) remaining across ${perFile.size} file(s):")
            perFile.entries.sortedByDescending { it.value }.take(25).forEach { (path, n) ->
                logger.lifecycle(String.format("  %4d  %s", n, path))
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Navigation
    implementation(libs.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Hilt integration for Compose navigation
    implementation(libs.androidx.hilt.navigation.compose)

    // Glance (home-screen App Widget)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Cloud backup: Google Sign-In (Drive appDataFolder scope) + Drive REST v3 over OkHttp
    implementation(libs.play.services.auth)
    implementation(libs.okhttp)

    // Cloud backup: periodic + debounced background upload
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Baseline profile: profileinstaller applies the packaged ART profile at install time,
    // the :baselineprofile module generates it (see that module's BaselineProfileGenerator).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    // Markdown rendering for task notes
    implementation(libs.markwon.core)

    // Tasker plugin (Create Task action)
    implementation(libs.tasker.plugin.library)

    // PDF Info dictionary (Title/Author/Subject/Keywords) — android.graphics.pdf.PdfDocument
    // has no metadata API, so exported PDFs get a real doc-info pass through this after
    // being rendered with PdfDocument.
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
