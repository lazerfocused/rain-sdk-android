plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

version = libs.versions.rain.sdk.get()
// Same group as :rain-core / :rain-portal so androidx @RestrictTo(LIBRARY_GROUP) treats the
// three as one library group.
group = "xyz.rain"

android {
    namespace = "com.rain.sdk.privy"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Port, registry, capability interfaces, models — exposed to consumers.
    api(project(":rain-core"))

    // No real Privy SDK dependency yet — this is a scaffold.
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
}
