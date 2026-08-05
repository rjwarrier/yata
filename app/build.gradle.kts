import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
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

val debugKeystoreFile = rootProject.file("debug.keystore")

android {
    namespace = "com.mj.yata"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mj.yata"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.91 beta"

        // Stamped fresh into every build (not just release) so Help & About's "Build N.DDMMYYYYHHmm"
        // line reflects exactly when this particular APK was assembled, not just the day — the
        // minute resolution is what actually tells apart two same-versionCode debug builds from
        // the same dev loop. HHmm is 24-hour (SimpleDateFormat's H, not h).
        buildConfigField(
            "String",
            "BUILD_DATE",
            "\"" + SimpleDateFormat("ddMMyyyyHHmm").format(Date()) + "\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Debug signing pinned to a keystore shared across this project's dev machines, so a
        // debug build from any of them installs over the others without an uninstall first.
        // Deliberately not ~/.android/debug.keystore: that one is the machine-wide default every
        // other Android project on the box signs with, and swapping it there would force an
        // uninstall on all of them instead. Gitignored (*.keystore) — copy it between machines by
        // hand. Absent, the debug build falls back to the default key rather than failing.
        if (debugKeystoreFile.exists()) {
            getByName("debug") {
                storeFile = debugKeystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
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
        debug {
            // Adds the en-XA and ar-XB pseudolocales, selectable from the device's language
            // settings on a debug build. en-XA pads every resource-backed string with accents and
            // extra characters, so anything that stays plain English is still hardcoded, and any
            // clipped or overflowing layout is one that won't survive a longer language. ar-XB is
            // right-to-left, which surfaces layouts that assume left-to-right. Both work with no
            // translation written, which is what makes them useful this early.
            isPseudoLocalesEnabled = true
        }
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
    // Domain models (Task, Project, ...) carry List<String> fields that make the Compose
    // compiler infer the whole class unstable, since it can't prove the instance isn't a mutable
    // ArrayList. With strong skipping on, an unstable parameter is compared by identity, so every
    // Room emission (which allocates fresh Task objects) fails the check and recomposes every
    // visible TaskRow, not just the one that changed. These classes are all immutable data
    // classes assembled by data/mapper, so promising stability here is safe.
    composeCompiler {
        stabilityConfigurationFile.set(
            rootProject.layout.projectDirectory.file("app/compose_stability.conf")
        )
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

    androidResources {
        // Generates res/xml/locales_config and wires android:localeConfig into the merged
        // manifest from whichever values-<code>/ folders exist, so Android 13+ shows YATA in
        // the system's per-app language picker. Adding a language is then just adding the
        // folder — there is no hand-maintained list to forget to update.
        // The source locale of the unqualified values/ folder is declared in res/resources.properties.
        generateLocaleConfig = true
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
        // `text = "..."` is the big one: it's how a literal is written inside any multi-line
        // Text( ) call, which is most of them in this codebase. The original pattern only matched
        // the single-line Text("...") form, so it reported 3 while ~155 sat one line lower and
        // the count read as "essentially done" for months.
        //
        // label/placeholder/title cover the text-carrying slot parameters. All of these can
        // false-positive on a non-UI `text =` assignment; that's the right trade for an advisory
        // count — an over-report gets checked and dismissed, an under-report never gets looked at.
        val pattern = Regex(
            """(Text\(\s*"[^"]{2,}"""" +
                """|text\s*=\s*"[^"]{2,}"""" +
                """|contentDescription\s*=\s*"[^"]{2,}"""" +
                """|(?:label|placeholder|title)\s*=\s*"[^"]{2,}")"""
        )
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

/**
 * Refuses to run instrumented tests unless the device is explicitly declared disposable.
 *
 * `connectedAndroidTest` is not a read-only operation. It reinstalls the app, and the tests then
 * add, complete and delete real rows in whatever database is on the device — so pointing it at the
 * phone someone keeps their actual tasks on corrupts or destroys them. It used to uninstall
 * afterwards too, wiping everything; `leaveApksInstalledAfterRun` in gradle.properties stops that
 * part, but the writes during the run remain.
 *
 * The opt-in is a property rather than a comment because the failure mode is silent: the command
 * looks like every other Gradle task right up until the data is gone.
 *
 *   ./gradlew :app:connectedDebugAndroidTest -PdisposableDevice
 */
// Both values are read here, at configuration time. Touching `project` from inside doFirst is an
// error under the configuration cache, which this build has enabled.
val deviceIsDisposable = project.hasProperty("disposableDevice")
tasks.matching { it.name.startsWith("connected") }.configureEach {
    val taskPath = path
    doFirst {
        if (!deviceIsDisposable) {
            throw GradleException(
                "Refusing to run instrumented tests: they write to the database on the connected " +
                    "device and can destroy real data.\n" +
                    "Only run them against an emulator or a spare device, and say so explicitly:\n" +
                    "    ./gradlew $taskPath -PdisposableDevice"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
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

    // Self-hosted backup: SFTP to a user's own server. sshj needs Bouncy Castle registered as a
    // security provider (see YataApplication) for algorithms Android's stock providers don't
    // cover (Ed25519, curve25519-sha256), which a lot of real-world OpenSSH servers default to.
    //
    // sshj's own transitive Bouncy Castle (bcprov/bcpkix-jdk18on) is excluded here: pdfbox-android
    // below already pulls a different artifact for the exact same classes/packages
    // (bcprov/bcpkix-jdk15to18), and Android's build fails at dex-merge time ("Duplicate class")
    // when both are present, no matter which version wins. The jdk15to18 build pdfbox-android
    // brings is functionally complete for sshj's needs -- Ed25519/X25519 support has been in
    // Bouncy Castle since well before that artifact's baseline.
    implementation(libs.sshj) {
        exclude(group = "org.bouncycastle")
    }

    // Self-hosted backup: FTP/FTPS to a user's own server. Apache Commons Net's FTPSClient
    // handles TLS via the platform's own SSLContext/trust store -- no reflection-based algorithm
    // lookup the way sshj+Bouncy Castle needs, so no ProGuard keep rules or provider registration
    // required for this one.
    implementation(libs.commons.net)

    // Periodic and debounced self-hosted/local backup work
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
    // org.json ships in android.jar as method stubs that throw at runtime, so anything touching
    // JSONObject is untestable on the JVM without a real implementation on the test classpath.
    testImplementation(libs.org.json)
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
