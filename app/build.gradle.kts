plugins {
    alias(libs.plugins.android.application)
    // AGP 9.0+ has Kotlin support built in -- org.jetbrains.kotlin.android is no longer applied
    // (https://kotl.in/gradle/agp-built-in-kotlin). The Compose compiler and serialization
    // plugins are separate features and still need to be applied explicitly.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.outlook_calendar_integration_android"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.outlook_calendar_integration_android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Derived from the developer's own signing certificate (see SETUP.md for how to
        // compute the real value). Used to build the MSAL redirect URI intent filter below.
        manifestPlaceholders["msalRedirectUriHash"] = "PLACEHOLDER_SIGNATURE_HASH"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

// Seeds the gitignored real MSAL config from the checked-in template on every build where it's
// missing, so a fresh clone builds with zero manual setup (SETUP.md then walks the user through
// replacing the placeholder values with their real Azure app-registration values).
val ensureMsalConfig by tasks.registering {
    group = "setup"
    description = "Seeds src/main/res/raw/msal_config.json from the template if it doesn't already exist (see SETUP.md)."
    val templateFile = layout.projectDirectory.file("msal_config.json.template")
    val destFile = layout.projectDirectory.file("src/main/res/raw/msal_config.json")
    inputs.file(templateFile)
    outputs.file(destFile)
    doLast {
        val dest = destFile.asFile
        if (!dest.exists()) {
            dest.parentFile.mkdirs()
            templateFile.asFile.copyTo(dest, overwrite = false)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(ensureMsalConfig)
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Lifecycle / ViewModel / Navigation
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)

    // MSAL (Azure AD / Entra ID sign-in)
    implementation(libs.msal)

    // Networking: Retrofit + kotlinx.serialization for Microsoft Graph
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // java.time on minSdk 24 (native support starts at API 26)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
}
