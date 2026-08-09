import java.time.Duration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
}

// Android and JVM only. Apple gets SwiftUI: a Compose surface on iOS fights the
// native app it would be embedded in, and an app that already draws its own
// chrome in SwiftUI does not want a second toolkit in the process.
kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "tv.nomercy.player.video.ui"
        compileSdk = 36
        minSdk = 29

        // Robolectric reads the merged manifest and the resources with it.
        // Without them the Compose test host inflates against nothing and every
        // test fails on a missing theme rather than on what it measures.
        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = true
        }

        // The render gate runs on hardware because that is the only place a
        // decoder writes to a real surface. Robolectric cannot answer "did the
        // engine paint" — it has no compositor to paint into.
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
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        // The frame path, shared by the two targets that have one.
        //
        // A software-decoded engine hands over a buffer of pixels and something
        // has to turn that into an ImageBitmap every frame. The bitmap differs —
        // Skia on the desktop, android.graphics on the phone — but the sink, the
        // reuse discipline and the canvas that draws it do not, and the parts
        // that went wrong invisibly here (the byte order, the stride, whether a
        // reused bitmap repaints at all) are exactly the parts a second copy
        // would get wrong again. Apple stays out of it: AVPlayer renders into a
        // layer and has no frames to receive.
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
            }
        }
        jvmMain { dependsOn(jvmSharedMain) }
        androidMain {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(libs.androidx.media3.ui)
                implementation(libs.androidx.media3.exoplayer)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
        }
        jvmTest.dependencies {
            // Skiko's native runtime, without which the desktop Compose test
            // host dies in its static initializer. currentOs picks the right
            // platform artifact, which is what lets this run on a Windows dev
            // box and a Linux runner from the same line.
            implementation(compose.desktop.currentOs)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
            implementation(libs.androidx.media3.exoplayer)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

// Compose Multiplatform's resource plugin registers this task for the device
// test source set and never gives it an output directory, so configuring the
// build fails before a single test runs. This module ships no Compose resources
// — the control is drawn, not loaded — so there is nothing for it to copy and
// nothing lost by switching it off.
tasks.matching { it.name.startsWith("copyAndroidDeviceTestComposeResources") }.configureEach {
    enabled = false
}

// Compose Desktop's tests render through Skiko, which reaches for a GPU and a
// display. A CI runner has neither, and the failure is not an error — it is a
// test task that sits there until the job's timeout, with the last line of the
// log being the task that started.
//
// Software rendering and headless AWT are what make an offscreen Compose test
// actually offscreen.
tasks.withType<Test>().configureEach {
    // A temp directory inside the build, not the machine's.
    //
    // Conscrypt extracts a native .so to `java.io.tmpdir` and loads it, and
    // the self-hosted runner executes jobs in a container whose /tmp will not
    // take one: every Android host test failed with
    // `UnsatisfiedLinkError: Failed creating temp file
    // (/tmp/libconscrypt_openjdk_jni-linux-x86_64....so)`.
    //
    // Set HERE as well as at the root, because a root `tasks.withType<Test>()`
    // configures the root project's tasks and this module's are its own — the
    // root-only fix reported green once on a build whose tests were UP-TO-DATE
    // from the runner's persistent cache, then failed the moment they ran.
    val temporary: java.io.File = layout.buildDirectory.dir("tmp/test-jvm").get().asFile
    systemProperty("java.io.tmpdir", temporary.absolutePath)
    doFirst { temporary.mkdirs() }

    systemProperty("skiko.renderApi", "SOFTWARE")
    systemProperty("java.awt.headless", "true")

    // A bound and a running commentary, because the first two attempts at this
    // were guesses. The task starting and the log going quiet says nothing
    // about which test stopped; naming each one as it starts does, and the
    // timeout turns a job that runs to its cap into a red task with that name
    // still in the log.
    timeout.set(Duration.ofMinutes(8))
    testLogging {
        events("started", "passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// Its own coordinate.
//
// Without this the chrome is a module in a repository rather than something
// an application can depend on: the engine published alone, and the drop-in
// view reachable only by checking the repository out. An application that
// draws its own is meant to be able to take the engine and leave this, which
// is the whole reason it is a second artifact and not a source set.
mavenPublishing {
    publishToMavenCentral()

    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}

// Which engine the desktop gates run against, forwarded from the command line.
//
// In THIS file rather than the root's: `tasks.withType<Test>()` at the root
// configures the root project's tasks, and the desktop gates are this
// subproject's — so the flag was accepted, ignored, and the gate reported
// "engine: mpv" while being asked for vlc.
tasks.withType<Test>().configureEach {
    for (name in listOf("nomercy.video.engine", "jna.library.path")) {
        providers.gradleProperty(name).orNull?.let { value -> systemProperty(name, value) }
    }
}
