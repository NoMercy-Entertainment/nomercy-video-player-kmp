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
        commonTest.dependencies {
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
    signAllPublications()
}
