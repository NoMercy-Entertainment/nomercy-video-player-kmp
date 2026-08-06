import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.skie)
    alias(libs.plugins.detekt)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.maven.publish)
    // Applied for real in :ui-compose; declared here so the subproject can
    // take them by alias without restating a version.
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "tv.nomercy.player.video"
        compileSdk = 36
        minSdk = 29

        // Robolectric reads the merged manifest and the resources with it, and
        // the migrated engine asks the Context for its form factor and its heap
        // before it builds anything.
        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }

        // The engine gate runs on hardware, because an ExoPlayer that decodes
        // on a JVM stub proves nothing about the one that ships. Passthrough
        // and tunneling in particular are answered by an HDMI sink, which no
        // emulator has.
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
            }
        }
    }

    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
            }
        }
    }

    val videoXcf: XCFrameworkConfig = XCFramework("NoMercyVideoPlayer")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
        tvosArm64(),
        tvosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "NoMercyVideoPlayer"
            isStatic = true
            binaryOption("bundleId", "tv.nomercy.player.video")

            // Core's declarations are named all over this library's public API —
            // PlayerKey, PlayState, the event registry — so without this the
            // framework ships signatures a Swift file cannot write down. It is
            // linked either way; exporting is what puts it in the headers.
            //
            // Found by a tvOS view that could see TvChromeUi and not the key
            // type its own method takes.
            export(libs.nomercy.player.core)

            videoXcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // By coordinate, not by project path. settings.gradle.kts
            // substitutes the sibling checkout when there is one, so the same
            // line works against a published core and against a local edit.
            api(libs.nomercy.player.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // The television's control protocol is ordinary HTTP and an event
            // stream, so the library owns the whole transport rather than
            // asking a consumer for one. That is the difference from the music
            // side, where the wire is SignalR and the application owns it.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        jvmTest.dependencies {
            // The surface gate asks the class what it exposes, which is the only
            // way to notice a method renamed, never written, or invented here.
            // jvmTest-only: a reflection library in the shipped artifact would be
            // a megabyte every consumer carries so a test could ask a question at
            // build time.
            implementation(kotlin("reflect"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.startup)
            // The Android engine. Media3 is what every Android client already
            // uses, and reimplementing its buffering would be worse than
            // anything gained by owning it.
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.common)
            implementation(libs.androidx.media3.exoplayer.hls)
            implementation(libs.androidx.media3.datasource.okhttp)
            implementation(libs.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
        commonTest.dependencies {
            // The shipped stand-ins rather than a private copy. The Apple
            // conformance runner needs a backend too, and a second fake written
            // in Swift would be two answers to what the engine does.
            implementation(libs.nomercy.player.core.testing)
            // A transport tested without a socket: the mock engine hands back a
            // real byte channel, so the stream reader takes the same path it
            // takes against a television.
            implementation(libs.ktor.client.mock)
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            // The migrated pieces ask a Context for its form factor and its
            // heap, which is a question only a real Android framework answers.
            implementation(libs.robolectric)
            implementation(libs.okhttp.mockwebserver)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.common)
        }
    }
}

skie {
    analytics {
        enabled.set(false)
    }
}

detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/jvmTest/kotlin",
        "src/androidMain/kotlin",
        "src/appleMain/kotlin",
        "src/jvmMain/kotlin",
        // The Compose module is a separate Gradle project but the same codebase,
        // and a rule that only applies to part of a repo is a rule people learn
        // to route around.
        "ui-compose/src/commonMain/kotlin",
        "ui-compose/src/commonTest/kotlin",
        "ui-compose/src/androidMain/kotlin",
        "ui-compose/src/androidHostTest/kotlin",
        "ui-compose/src/jvmMain/kotlin",
        "ui-compose/src/jvmTest/kotlin",
        "ui-compose/src/androidDeviceTest/kotlin",
    )
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

// The same 21 the Kotlin compilations target, said again because detekt does
// not read it. Left unsaid it takes whatever JVM gradle happens to be running
// on, and the Mac build host is on 26 — a target detekt has never heard of, so
// it refuses with "Invalid value (26) passed to --jvm-target" before any rule
// runs. A machine-dependent gate is one that passes or fails for reasons that
// have nothing to do with the code.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
}

@OptIn(kotlinx.validation.ExperimentalBCVApi::class)
apiValidation {
    klib {
        enabled = true
        // Not strict, and the reason changed under us. Kotlin/Native compiles
        // klibs on any host, which is why this was strict — but the subtitle
        // module's Apple targets now bind to a C library through cinterop, and
        // that needs the Xcode toolchain. On Linux those targets cannot be
        // built, and strict validation reads "could not build" as "the whole
        // public surface was deleted".
        //
        // The Apple surface is still checked, on the macOS job, where the answer
        // means something.
        strictValidation = false
    }
}

mavenPublishing {
    publishToMavenCentral()

    // Signed where there is a key, which is CI and nowhere else.
    //
    // Unconditional signAllPublications() takes publishToMavenLocal down with
    // it — "no configured signatory" — and that is the one command that proves
    // the module metadata carries every target before anything reaches Central.
    // The signature is still mandatory where it matters: the workflow sets
    // signingInMemoryKey from the repository secret, so a release without one
    // is a release that never ran through CI.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}

// The conformance fixtures are inputs to the tests that read them.
//
// They are read off disk by path rather than loaded as test resources, so
// without this Gradle sees no change when one is edited and reports the whole
// suite UP-TO-DATE. A vendored scenario nudged until a port passes it would
// then never even be measured — the gate does not fail, it does not run, and
// the build is green either way.
//
// Found by editing one on purpose and watching nothing happen.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree("contract") { include("*.json") },
        fileTree("scenarios") { include("*.json") },
    )
        .withPropertyName("conformanceFixtures")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // What this library IS, so a test can compare it to the core it RESOLVED.
    // The two are hand-written in different files and drifted for a whole
    // renumber without anything noticing — see CoreVersionPinTest.
    systemProperty("nomercy.library.version", providers.gradleProperty("VERSION_NAME").get())
}


// The conformance gate, by name.
//
// These tests already run inside jvmTest, so a red gate blocks `build` whether
// or not this task exists. What it adds is time and a name: it answers "has this
// port drifted from the ecosystem contract" in seconds, before the multiplatform
// build spends ten minutes arriving at the same answer under a heading that says
// nothing.
tasks.register<Test>("parityConformance") {
    group = "verification"
    description = "Checks the port against the vendored contract: surface, fixtures and behaviour."

    val jvmTest: Test = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath

    filter {
        includeTestsMatching("tv.nomercy.player.video.conformance.*")
        includeTestsMatching("tv.nomercy.player.conformance.*")
    }

    testLogging {
        events("failed")
        showStandardStreams = false
    }
}
