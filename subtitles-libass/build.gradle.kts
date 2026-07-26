import org.jetbrains.kotlin.konan.target.HostManager
import java.net.URI
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

// The libass-backed subtitle renderer, in its own module because a native
// subtitle library is a dependency a consumer should be able to decline. An app
// showing plain WebVTT takes the video library and never links libass.
kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidLibrary {
        namespace = "tv.nomercy.player.video.ass"
        compileSdk = 36
        minSdk = 29

        withHostTestBuilder {}.configure {}

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

    // Apple targets exist only on a Mac, for this module alone.
    //
    // They bind to a C library through cinterop, which needs the Xcode
    // toolchain. Declaring them on a Linux runner does not fail fast — it sits
    // in the Kotlin/Native compilation until the job times out, which reads as a
    // hung build rather than a missing toolchain.
    //
    // The rest of the library still declares them everywhere; only the module
    // that needs Xcode is gated. The macOS job is where the Apple surface is
    // compiled, tested and API-checked.
    val appleTargets = if (HostManager.hostIsMac) {
        listOf(iosArm64(), iosSimulatorArm64(), iosX64(), tvosArm64(), tvosSimulatorArm64())
    } else {
        emptyList()
    }

    // libass for Apple is a prebuilt XCFramework, fetched rather than compiled.
    //
    // Cross-compiling libass, freetype, fribidi and harfbuzz needs an autotools
    // toolchain, cmake and half an hour, and putting that in front of a Gradle
    // build would mean nobody could build this library on a fresh machine. It is
    // built once from upstream sources and published as a release asset;
    // fetchAppleLibass downloads and unpacks it.
    //
    // The cinterop is registered whether or not the artifact is present. A
    // missing one fails the Apple compilation with a message about a path, which
    // is a better answer than a source set that silently swaps itself out and
    // leaves a developer wondering why subtitles do nothing.
    appleTargets.forEach { target ->
        val slice: String = when (target.konanTarget.name) {
            "ios_arm64" -> "ios-arm64"
            "tvos_arm64" -> "ios-arm64"
            else -> "ios-arm64_x86_64-simulator"
        }
        target.compilations.getByName("main").cinterops.create("libass") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/libass.def"))
            val root: java.io.File = project.layout.buildDirectory.get().asFile.resolve("libass")
            compilerOpts("-I${root.resolve("ass.xcframework/$slice/Headers")}")
            extraOpts(
                "-libraryPath", root.resolve("ass.xcframework/$slice").path,
                "-libraryPath", root.resolve("freetype.xcframework/$slice").path,
                "-libraryPath", root.resolve("fribidi.xcframework/$slice").path,
                "-libraryPath", root.resolve("harfbuzz.xcframework/$slice").path,
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":"))
            // Core by coordinate as well as through the video library. The
            // project dependency carries core to the platform compilations but
            // not to the common metadata one, and without this the shared
            // source set cannot see the Plugin it extends.
            api(libs.nomercy.player.core)
        }
        androidMain.dependencies {
            implementation(libs.ass.kt)
        }
        jvmMain.dependencies {
            implementation(libs.jna)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
        }
    }
}

// The prebuilt Apple libass, fetched once and cached in the build directory.
//
// Pinned by tag and checked against a digest, because a native library that
// silently changed under a build is the kind of supply-chain problem that shows
// up as a crash on someone else's phone.
val libassArchive: String =
    "https://github.com/NoMercy-Entertainment/nomercy-video-player-kmp/releases/download/" +
        "libass-apple-0.17.5/libass-apple.tar.gz"
val libassDigest = "13d01bbd0666752a77447347781a2f5ce96d06c2ff33d7207f9ef2450c0c806e"

val fetchAppleLibass by tasks.registering {
    description = "Downloads the prebuilt libass XCFrameworks for Apple targets."
    group = "build setup"

    val target: java.io.File = layout.buildDirectory.get().asFile.resolve("libass")
    val archive: java.io.File = layout.buildDirectory.get().asFile.resolve("libass-apple.tar.gz")
    outputs.dir(target)

    doLast {
        if (target.resolve("ass.xcframework").isDirectory) return@doLast

        target.mkdirs()
        URI(libassArchive).toURL().openStream().use { input ->
            archive.outputStream().use { output -> input.copyTo(output) }
        }

        val digest: String = MessageDigest.getInstance("SHA-256")
            .digest(archive.readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        check(digest == libassDigest) {
            "libass archive digest mismatch: expected $libassDigest but got $digest"
        }

        providers.exec {
            commandLine("tar", "xzf", archive.path, "-C", target.path)
        }.result.get()
    }
}

tasks.matching { it.name.startsWith("cinteropLibass") }.configureEach {
    dependsOn(fetchAppleLibass)
}

// The klib surface check runs on macOS only, for this module alone.
//
// Its Apple targets bind to a C library through cinterop, which needs the Xcode
// toolchain. Anywhere else they do not compile, so the extracted surface is
// empty and the check reports the whole public API as deleted — a red build
// caused by the host rather than by a change. The macOS CI job runs it, which is
// where the answer means something.
tasks.matching { it.name == "klibApiCheck" }.configureEach {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
}
