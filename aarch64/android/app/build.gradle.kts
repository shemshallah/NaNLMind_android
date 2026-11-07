plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.nan0s.vm"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.nan0s.vm"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.4"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=none") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    // the image is stored uncompressed-friendly: it is 1.4 MB, mostly zeros
    androidResources { noCompress += "img" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Optional: refresh the bundled image from the x86 build (sh x86/tools/mkall.sh -> x86/build/nan0s.img). This project lives at <repo>/aarch64/android/.
tasks.register<Copy>("syncImage") {
    from("../../../x86/build/nan0s.img")
    into("src/main/assets")
    onlyIf { file("../../../x86/build/nan0s.img").exists() }
}


// Compile the shared NaN16 training guest from its source before Android packaging.
tasks.register("compileTrainingGuest") {
    val guestSource = file("../../../src/training/n16_train.nas")
    val guestOutput = file("src/main/assets/n16_train.nan")
    inputs.file(guestSource)
    outputs.file(guestOutput)
    doLast {
        guestOutput.parentFile.mkdirs()
        guestOutput.outputStream().use { stream ->
            exec {
                commandLine("awk", "-f", file("../../../tools/nmk.awk"), "-v", "CMD=emit", guestSource)
                standardOutput = stream
            }
        }
    }
}
// Compile the deterministic sample scheduler as a separately testable NaN16 guest.
tasks.register("compileScheduleGuest") {
    val guestSource = file("../../../src/training/n16_schedule.nas")
    val guestOutput = file("src/main/assets/n16_schedule.nan")
    inputs.files(guestSource, file("../../../tools/nmk.awk"))
    outputs.file(guestOutput)
    doLast {
        guestOutput.parentFile.mkdirs()
        guestOutput.outputStream().use { stream ->
            exec {
                commandLine("awk", "-f", file("../../../tools/nmk.awk"), "-v", "CMD=emit", guestSource)
                standardOutput = stream
            }
        }
        check(guestOutput.isFile && guestOutput.length() > 0) {
            "NaN16 sample scheduler guest was not generated"
        }
    }
}
// Compile a dedicated persistent protocol VM image and its guest label map.
tasks.register("compileProtocolGuest") {
    val guestSource = file("../../../src/nannet/n16t_android_guest.nas")
    val guestOutput = file("src/main/assets/n16_protocol.nan")
    val labelsOutput = file("src/main/assets/n16_protocol.labels")
    inputs.files(
        guestSource,
        file("../../../src/nannet/nannet.nas"),
        file("../../../src/nannet/overbootstrap_route.nas"),
        file("../../../src/nannet/train_protocol.nas"),
        file("../../../src/nannet/n16t_android_abi.nas"),
        file("../../../tools/nmk.awk")
    )
    outputs.files(guestOutput, labelsOutput)
    doLast {
        guestOutput.parentFile.mkdirs()
        guestOutput.outputStream().use { guest ->
            exec {
                commandLine(
                    "awk", "-f", file("../../../tools/nmk.awk"),
                    "-v", "CMD=emit",
                    "-v", "LABELS=${labelsOutput.absolutePath}",
                    guestSource
                )
                standardOutput = guest
            }
        }
        check(labelsOutput.isFile && labelsOutput.length() > 0) {
            "NaN16 protocol guest label map was not generated"
        }
        val guestLabels = labelsOutput.readLines().mapNotNull { line ->
            line.trim().split(Regex("\\s+")).takeIf { it.size == 2 }?.first()
        }.toSet()
        val requiredLabels = setOf(
            "n16_android_build", "n16_android_route",
            "n16_android_advance", "n16_android_abort", "n16_android_reset"
        )
        check(guestLabels.containsAll(requiredLabels)) {
            "NaN16 protocol guest is missing entry points: ${requiredLabels - guestLabels}"
        }
    }
}
// Compile the composed rule + typed-object admission gate for Android VM calls.
tasks.register("compileMathAdmissionGuest") {
    val guestSource = file("../../../src/training/n16_math_admission_guest.nas")
    val guestOutput = file("src/main/assets/n16_math_admission.nan")
    val labelsOutput = file("src/main/assets/n16_math_admission.labels")
    val guestSources = listOf(
        guestSource,
        file("../../../src/mth/mth_preflight_gate.nas"),
        file("../../../src/mth/mth_preflight.nas"),
        file("../../../src/mth/mth_admission.nas"),
        file("../../../tools/nmk.awk")
    )
    inputs.files(guestSources)
    outputs.files(guestOutput, labelsOutput)
    doLast {
        guestOutput.parentFile.mkdirs()
        guestOutput.outputStream().use { guest ->
            exec {
                commandLine(
                    "awk", "-f", file("../../../tools/nmk.awk"),
                    "-v", "CMD=emit",
                    "-v", "LABELS=${labelsOutput.absolutePath}",
                    guestSource
                )
                standardOutput = guest
            }
        }
        check(labelsOutput.isFile && labelsOutput.length() > 0) {
            "NaN16 math admission guest label map was not generated"
        }
        val guestLabels = labelsOutput.readLines().mapNotNull { line ->
            line.trim().split(Regex("\\s+")).takeIf { it.size == 2 }?.first()
        }.toSet()
        val requiredLabels = setOf(
            "n16_math_admission_entry", "n16_math_candidate", "n16_math_rule",
            "n16_math_left", "n16_math_right", "n16_math_contract",
            "n16_math_capabilities", "n16_math_output", "n16_math_result",
            "n16_math_left_payload", "n16_math_right_payload", "n16_math_output_payload"
        )
        check(guestLabels.containsAll(requiredLabels)) {
            "NaN16 math admission guest is missing labels: ${requiredLabels - guestLabels}"
        }
    }
}
tasks.register("verifyRuntimeAssetAllowlist") {
    dependsOn("compileTrainingGuest", "compileScheduleGuest", "compileProtocolGuest", "compileMathAdmissionGuest")
    doLast {
        val allowed = setOf(
            "nan0s.img", "disk.nanfs", "n16_train.nan", "n16_schedule.nan",
            "n16_protocol.nan", "n16_protocol.labels", "n16_math_admission.nan", "n16_math_admission.labels"
        )
        val actual = fileTree("src/main/assets").files.map { it.name }.toSet()
        val unexpected = actual - allowed
        val missing = allowed - actual
        check(unexpected.isEmpty()) { "Unexpected runtime assets: $unexpected" }
        check(missing.isEmpty()) { "Missing runtime assets: $missing" }
    }
}
tasks.named("preBuild").configure { dependsOn("verifyRuntimeAssetAllowlist") }
