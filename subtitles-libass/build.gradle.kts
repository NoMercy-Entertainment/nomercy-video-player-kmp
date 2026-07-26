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

    listOf(iosArm64(), iosSimulatorArm64(), iosX64(), tvosArm64(), tvosSimulatorArm64())

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
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
        }
    }
}
