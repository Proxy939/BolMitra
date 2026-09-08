import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    // Kotlin itself needs no plugin: AGP 9 built-in Kotlin handles compilation.
    // The Compose compiler plugin is still applied separately.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.bolmitra"
    compileSdk = 37

    // §4.8.2 / V33: r28+ is MANDATORY — 16 KB page alignment is the default from r28,
    // and sherpa-onnx's prebuilt libonnxruntime4j_jni.so fails it, so we build from
    // source. This is also AGP 9's own default (r28c), pinned here because AGP's release
    // notes recommend stating it explicitly rather than inheriting it.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "org.bolmitra"
        // ARCHITECTURE.md §4.8.1: minSdk 28 is what satisfies the PS's "Android 9+".
        // targetSdk does NOT restrict device support (V36) — only minSdk does.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase0"

        // §4.1 / §4.8.2: arm64-v8a only. Halves the native payload and int8 GEMM is
        // far better on AArch64. Confirm in Phase 0 whether any target device needs
        // armeabi-v7a before this is locked.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 stays on for release: §4.8.7 requires an APK size gate.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Export Room schemas so migrations can be tested rather than hoped for. O8 in §12 flags
    // the default fallbackToDestructiveMigration as a real hazard: on an app update it would
    // silently delete the correction outbox, losing teacher corrections that had not synced.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // §4.8.3 / V33: uncompressed .so is required for 16 KB page alignment to hold
        // at install time. Set now so it is not forgotten when sherpa-onnx arrives.
        jniLibs {
            useLegacyPackaging = false

            // Drop every ABI except the one §4.1 ships. Without this, mergeDebugNativeLibs fails
            // outright on `2 files found with path 'lib/x86/libonnxruntime.so'`.
            //
            // The cause is an inconsistency in the sherpa-onnx release, not in this build: the
            // "static-link-onnxruntime" AAR is only static for arm64-v8a, armeabi-v7a and x86_64.
            // For x86 alone it ships a separate 24.7 MB libonnxruntime.so, which then collides
            // with the one inside onnxruntime-android. So the §5.2 "exactly one ONNX Runtime"
            // property was never true for x86 — it just never mattered, because x86 is not a
            // target. abiFilters alone does not prevent this; the merge runs before it.
            //
            // Excluding rather than pickFirst on purpose: pickFirst would silently choose one of
            // two different ORT builds for an ABI we do not ship, which is a coin flip hidden in
            // the build. These ABIs are simply not wanted.
            excludes += setOf("lib/x86/**", "lib/x86_64/**", "lib/armeabi-v7a/**")
        }
    }
}

// ---------------------------------------------------------------------------------------
// sherpa-onnx AAR fetch — ARCHITECTURE.md §5.2 "Resolution 0" (Revision 21)
//
// The static-link variant embeds ONNX Runtime, so there is exactly ONE .so and no separate
// libonnxruntime.so to collide with the MT tier later. It is not on Maven Central, so it is
// fetched from the pinned GitHub release and SHA-256 verified rather than committed.
//
// The hash is not decoration. This is a 37 MB third-party binary heading for government
// tablets; §4.7 already treats binary integrity as a trust boundary. A mismatch FAILS the
// build loudly rather than silently shipping something unexpected.
// ---------------------------------------------------------------------------------------
val sherpaVersion = libs.versions.sherpaOnnx.get()
val sherpaAarName = "sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar"
val sherpaAarSha256 = "220d7cb25ac6e57ec34082bc2551ebd7ec7d4d96cbfea520839e208f92347837"
val sherpaAarUrl =
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/$sherpaAarName"
val sherpaAarFile = layout.projectDirectory.file("libs/$sherpaAarName").asFile

val fetchSherpaOnnxAar = tasks.register("fetchSherpaOnnxAar") {
    description = "Downloads and SHA-256 verifies the sherpa-onnx static-link AAR."
    val target = sherpaAarFile
    val url = sherpaAarUrl
    val expected = sherpaAarSha256

    // Declaring the AAR as an output is enough for up-to-date checking. A local `fun` is
    // used inside doLast rather than a script-level helper, because the configuration cache
    // cannot serialize references to Gradle script objects.
    outputs.file(target)

    doLast {
        fun sha256(f: File): String =
            MessageDigest.getInstance("SHA-256")
                .digest(f.readBytes())
                .joinToString("") { "%02x".format(it) }

        if (!target.isFile || sha256(target) != expected) {
            target.parentFile.mkdirs()
            logger.lifecycle("Fetching $url")
            URI(url).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actual = sha256(target)
        if (actual != expected) {
            target.delete()
            throw GradleException(
                "sherpa-onnx AAR SHA-256 mismatch.\n  expected: $expected\n  actual:   $actual\n" +
                    "The downloaded artifact is not the pinned one. Refusing to build.",
            )
        }
        logger.lifecycle("sherpa-onnx AAR verified (${target.length() / 1024 / 1024} MB)")
    }
}

tasks.named("preBuild") { dependsOn(fetchSherpaOnnxAar) }

dependencies {
    // Local AAR. settings.gradle.kts sets FAIL_ON_PROJECT_REPOS, which rules out a
    // flatDir repository, so a file-based dependency is the correct route here.
    implementation(fileTree("libs") { include("*.aar") })

    // T1 MT tier. The second ONNX Runtime in the build, on purpose: the sherpa-onnx AAR embeds
    // ORT statically and exposes no `ai.onnxruntime.*` classes, so there is no way to reach a
    // generic session through it. See the note in libs.versions.toml for the size and the
    // 16 KB alignment verification.
    implementation(libs.onnxruntime.android)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
