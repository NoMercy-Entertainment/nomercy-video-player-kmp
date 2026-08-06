import org.jetbrains.kotlin.konan.target.HostManager
import java.net.URI
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
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
    // A slice per platform, not per architecture. tvOS pointed at the iOS slice
    // here for a while and it reads as though it should work — same instruction
    // set, same toolchain — but a Mach-O object carries the platform it was built
    // for, and the linker refuses the mix outright: "building for
    // 'tvOS-simulator', but linking in object file built for 'iOS-simulator'".
    appleTargets.forEach { target ->
        val slice: String = when (target.konanTarget.name) {
            "ios_arm64" -> "ios-arm64"
            "tvos_arm64" -> "tvos-arm64"
            "tvos_simulator_arm64" -> "tvos-arm64-simulator"
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
        // One source set for the two platforms that can run JNA.
        //
        // libass is driven through the same C entry points everywhere, and this
        // module had two bindings to it: our own JNA interface on the desktop
        // and a third-party JNI wrapper on Android. Two bindings is two places
        // for a cache limit or a frame size to be set differently, which is how
        // the same subtitle renders correctly on one surface and not the other.
        //
        // JNA runs on Android, so there is no reason for the second one. The
        // default hierarchy has no jvm+android group, so it is declared.
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Compile only: the binding needs the JNA API here, and each
                // platform below brings the packaging it can actually load.
                // Shipping the artifact from this set as well puts every
                // com.sun.jna class into an APK twice and the merge fails.
                compileOnly(libs.jna)
            }
        }
        androidMain.get().dependsOn(jvmAndroidMain)
        jvmMain.get().dependsOn(jvmAndroidMain)

        jvmMain.dependencies {
            implementation(libs.jna)
        }

        // The same JNA, in the packaging Android can load.
        //
        // The jar carries libjnidispatch for the desktop as JVM resources, which
        // an APK cannot dlopen; the aar carries one per ABI in jniLibs. Without
        // it Native.load throws for jnidispatch, not for libass, so the error
        // names the wrong library and reads as though our build is missing.
        androidMain.dependencies {
            implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
        }

        commonMain.dependencies {
            api(project(":"))
            // The portable filesystem the font cache writes through, so its
            // eviction and recovery rules are one implementation rather than
            // three.
            implementation(libs.okio)
            // Mutex. libass is not reentrant and the plugin is what orders the
            // calls into it.
            implementation(libs.kotlinx.coroutines.core)
            // Core by coordinate as well as through the video library. The
            // project dependency carries core to the platform compilations but
            // not to the common metadata one, and without this the shared
            // source set cannot see the Plugin it extends.
            api(libs.nomercy.player.core)
        }
        commonTest.dependencies {
            // A filesystem the tests own. Thirty-day eviction cannot be waited
            // for, and a real temp directory races whatever else is writing there.
            implementation(libs.okio.fakefilesystem)
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
        "libass-apple-0.17.5-tvos/libass-apple.tar.gz"
val libassDigest = "dfd19c5bdbdefd36efb6e63c7dd8cd69436a2c6c3c427c1edecaf0c75fa31f67"

val fetchAppleLibass by tasks.registering {
    description = "Downloads the prebuilt libass XCFrameworks for Apple targets."
    group = "build setup"

    val target: java.io.File = layout.buildDirectory.get().asFile.resolve("libass")
    val archive: java.io.File = layout.buildDirectory.get().asFile.resolve("libass-apple.tar.gz")
    outputs.dir(target)

    doLast {
        // Keyed on the digest, not on "something is already unpacked here".
        // Bumping the archive to one carrying tvOS slices left every machine
        // that had ever built this module on the old iOS-only copy, and the
        // failure was a header not found rather than anything mentioning a
        // stale download.
        val stamp: java.io.File = target.resolve(".digest")
        if (stamp.isFile && stamp.readText() == libassDigest) return@doLast

        target.deleteRecursively()
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

        stamp.writeText(libassDigest)
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
// And the DUMP is guarded the same way, which it was not.
//
// Guarding only the check leaves the hole open at the other end: `apiDump` at
// the repository root recurses into here, finds no Apple targets to extract
// from, and writes the surface it can see — which is the file with two hundred
// and thirty lines removed. Nothing goes red locally, because the check that
// would have said so is skipped on this host for the very same reason. The
// deletion travels in whatever commit happened to be open and CI fails on
// macOS, pointing at a module the change never touched. That has now happened
// twice.
//
// So a dump on a host that cannot compile the targets is not a dump. Skipped
// rather than allowed to write a partial answer over a complete one.
tasks.matching { it.name == "klibApiCheck" || it.name == "klibApiDump" }.configureEach {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
}

// Where a cue actually lands, off-screen, so a geometry bug can be told apart
// from a drawing one.
val assGeometryProbe by tasks.registering(JavaExec::class) {
    description = "Renders one cue at a given surface size and reports its pixel extents."
    group = "verification"

    val test = kotlin.jvm().compilations.getByName("test")
    classpath = files(test.output.allOutputs, test.runtimeDependencyFiles)
    mainClass.set("tv.nomercy.player.video.ass.bench.AssGeometryProbe")
    dependsOn(tasks.named("jvmTestClasses"))

    for (name in listOf("libass", "track", "fonts", "out", "time", "width", "height")) {
        systemProperty("nomercy.probe.$name", providers.gradleProperty("probe.$name").getOrElse(""))
    }
}

// What a moving ASS subtitle costs on the desktop, on the tracks that are slow.
//
// A task rather than a test: it reports numbers, it takes minutes, and a suite
// that fails on a timing is a suite that fails on whatever else the machine was
// doing. The gate for "is it fast enough" is read by a person looking at the
// distribution, not by an assertion on an average.
val assBenchmark by tasks.registering(JavaExec::class) {
    description = "Measures libass render, copy-out and composite cost on real anime tracks."
    group = "verification"

    val test = kotlin.jvm().compilations.getByName("test")
    classpath = files(test.output.allOutputs, test.runtimeDependencyFiles)
    mainClass.set("tv.nomercy.player.video.ass.bench.AssRenderBenchmark")
    dependsOn(tasks.named("jvmTestClasses"))

    // Both are paths on the machine running it — the libass build and the
    // staged tracks are gigabytes of anime, neither of which belongs in a repo.
    systemProperty("nomercy.bench.libass", providers.gradleProperty("bench.libass").getOrElse(""))
    systemProperty("nomercy.bench.assets", providers.gradleProperty("bench.assets").getOrElse(""))
    systemProperty("nomercy.bench.only", providers.gradleProperty("bench.only").getOrElse(""))
    systemProperty("nomercy.bench.bands", providers.gradleProperty("bench.bands").getOrElse("8"))
    // Overridable, because "is this frame slow or is this a collector pause"
    // is answered by running the same window under a different collector and
    // watching whether the maxima move. Every stage peaking at once is a pause;
    // one stage peaking is the code in it.
    jvmArgs(providers.gradleProperty("bench.jvmArgs").getOrElse("-Xmx2g").split(" "))
}

// Its own coordinate, and the reason this module exists at all.
//
// A consumer showing plain WebVTT takes the video library and never links
// libass. That promise is only real if the two are separate artifacts — a
// module inside the repository is reachable by checking the repository out and
// by nothing else, which is what this was: the testbed asked for it by
// coordinate and the coordinate did not exist.
mavenPublishing {
    publishToMavenCentral()

    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
}
