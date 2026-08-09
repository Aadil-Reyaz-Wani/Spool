plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.aadil.spool.core.model"
        compileSdk {
            version = release(36) {
                minorApiLevel = 1
            }
        }
        minSdk = 24
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    val generateAppConfig = tasks.register("generateAppConfig") {
        val outputDir = layout.buildDirectory.dir("generated/source/appconfig/commonMain")
        val versionNameProp = (findProperty("spool.versionName") as? String) ?: "1.3.3"
        val versionCodeProp = (findProperty("spool.versionCode") as? String)?.toIntOrNull() ?: 9
        inputs.property("versionName", versionNameProp)
        inputs.property("versionCode", versionCodeProp)
        outputs.dir(outputDir)
        doLast {
            val file = outputDir.get().file("com/aadil/spool/AppConfig.kt").asFile
            file.parentFile.mkdirs()
            file.writeText("""
                package com.aadil.spool

                object AppConfig {
                    const val VERSION_NAME: String = "$versionNameProp"
                    const val VERSION_CODE: Int = $versionCodeProp
                }
            """.trimIndent())
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateAppConfig.map { it.outputs.files })
            dependencies {
                implementation(libs.kotlinx.serialization.core)
            }
        }
    }
}
