import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

val supabaseProperties = providers.fileContents(
    rootProject.layout.projectDirectory.file("supabase.local.properties")
).asText.orElse("")

val generateSupabaseConfig = tasks.register("generateSupabaseConfig") {
    inputs.property("supabaseProperties", supabaseProperties)
    val outputDirectory = layout.buildDirectory.dir("generated/supabase/commonMain")
    outputs.dir(outputDirectory)
    doLast {
        val properties = Properties().apply { load(supabaseProperties.get().reader()) }
        val url = properties.getProperty("supabase.url", "").trim()
        val key = properties.getProperty("supabase.publishableKey", "").trim()
        val googleClientId = properties.getProperty("google.webClientId", "").trim()
        require(googleClientId.isEmpty() || googleClientId.matches(Regex("[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com"))) {
            "google.webClientId must be a Google OAuth Web client ID."
        }
        // Reject unsafe values before they can be embedded in any mobile artifact.
        require(url.isEmpty() || url.matches(Regex("https://[A-Za-z0-9.-]+(?::443)?/?"))) {
            "supabase.url must be an HTTPS origin without credentials, paths, query or fragment."
        }
        require(key.isEmpty() || key.matches(Regex("sb_publishable_[A-Za-z0-9_-]+"))) {
            "supabase.publishableKey must be a mobile sb_publishable_ key. Privileged keys and JWTs are not allowed."
        }
        val output = outputDirectory.get().file("com/aadil/spool/core/network/SupabaseBuildConfig.kt").asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            package com.aadil.spool.core.network

            internal object SupabaseBuildConfig {
                const val URL = "$url"
                const val PUBLISHABLE_KEY = "$key"
                const val GOOGLE_WEB_CLIENT_ID = "$googleClientId"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    android {
        namespace = "com.aadil.spool.core.network"
        compileSdk {
            version = release(36) {
                minorApiLevel = 1
            }
        }
        minSdk = 24
        withHostTestBuilder {}
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSupabaseConfig.map { it.outputs.files })
            dependencies {
                api(project(":core:model"))
                api(libs.supabase.auth)
                api(libs.supabase.storage)
                implementation(libs.ktor.client.core)
                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
            }
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("spool.supabase.live", providers.gradleProperty("supabaseLive").orElse("false").get())
    testLogging.showStandardStreams = true
}
