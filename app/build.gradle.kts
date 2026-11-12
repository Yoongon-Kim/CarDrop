import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Per-developer settings live in local.properties (never committed).
// See CLAUDE.md > "Local settings" for every key.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localFlag(key: String): Boolean = localProps.getProperty(key)?.trim() == "true"
fun localString(key: String): String = localProps.getProperty(key)?.trim().orEmpty()

// Kotlin copies BuildConfig values into the code that reads them, and an incremental build can keep
// the old copy after local.properties changes (a new key still looked empty). So any change to
// local.properties recompiles all Kotlin code.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    inputs.file(rootProject.file("local.properties")).withPropertyName("localProperties").optional()
}

android {
    namespace = "com.cardrop"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cardrop"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Switch each area from its fake to its real implementation.
        buildConfigField("boolean", "USE_REAL_VEHICLE", localFlag("cardrop.useRealVehicle").toString())
        buildConfigField("String", "NAV_APP", "\"${localString("cardrop.navApp").ifEmpty { "inapp" }}\"")
        buildConfigField("String", "CLAUDE_API_KEY", "\"${localString("cardrop.claudeApiKey")}\"")
        buildConfigField("String", "KAKAO_REST_KEY", "\"${localString("cardrop.kakaoRestKey")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.vehicle.sdk)
    implementation(libs.maplibre.android) // in-app navigator map (navi/NavMap.kt)
    implementation(libs.anthropic.java) // real AiService (ai/ClaudeAiService.kt), approved by the lead 2026-11-13

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    testImplementation(libs.json) // real org.json for parser tests (Android's is a stub in unit tests)
    debugImplementation(libs.androidx.ui.tooling)
}
