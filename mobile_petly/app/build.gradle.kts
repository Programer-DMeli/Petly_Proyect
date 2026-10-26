plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.petly_app"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.petly_app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("petlyIntegrationInstall").orNull == "true") {
                applicationIdSuffix = ".integration"
            }
            val apiBase = providers.gradleProperty("petlyApiBaseUrl").getOrElse("http://10.0.2.2:8080/")
            require(apiBase.endsWith("/") && apiBase.matches(Regex("https?://[A-Za-z0-9.:-]+/")))
            buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
            val adminBase = providers.gradleProperty("petlyAdminApiBaseUrl").getOrElse("http://10.0.2.2:8000/")
            require(adminBase.endsWith("/") && adminBase.matches(Regex("https?://[A-Za-z0-9.:-]+/")))
            buildConfigField("String", "ADMIN_API_BASE_URL", "\"$adminBase\"")
            isMinifyEnabled = false
            isShrinkResources = false
        }

        getByName("release") {
            optimization {
                enable = true
                packageScope = setOf(
                    "androidx.**",
                    "kotlin.**",
                    "kotlinx.**"
                )
            }
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://api.example.invalid/\""
            )
            val adminBase = providers.gradleProperty("petlyAdminApiBaseUrl").getOrElse("https://admin-api.example.invalid/")
            require(adminBase.endsWith("/") && adminBase.matches(Regex("https?://[A-Za-z0-9.:-]+/")))
            buildConfigField("String", "ADMIN_API_BASE_URL", "\"$adminBase\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/*.kotlin_module"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
