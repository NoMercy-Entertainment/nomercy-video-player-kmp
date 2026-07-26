import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
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
        androidMain.dependencies {
            implementation(libs.androidx.media3.ui)
            implementation(libs.androidx.media3.exoplayer)
        }
        jvmMain.dependencies {
            implementation(libs.vlcj)
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
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}
