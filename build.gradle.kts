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

        withHostTestBuilder {}.configure {}

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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
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
    )
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

@OptIn(kotlinx.validation.ExperimentalBCVApi::class)
apiValidation {
    klib {
        enabled = true
        strictValidation = true
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
}
