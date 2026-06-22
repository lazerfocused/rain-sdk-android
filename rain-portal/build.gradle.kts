plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

version = libs.versions.rain.sdk.get()
// Same group as :rain-core / :rain-privy so androidx @RestrictTo(LIBRARY_GROUP) treats the
// three as one library group (this module may use core's adapter kit; apps may not).
group = "xyz.rain"

android {
    namespace = "com.rain.sdk.portal"
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
    // Port, registry, capability interfaces, adapter kit, models — exposed to consumers.
    api(project(":rain-core"))

    // Portal SDK stays off the consumer compile classpath.
    implementation(libs.portal.android)

    // ERC-20 transfer calldata encoding (inherits the global bcprov-jdk18on exclusion).
    implementation(libs.web3j.core) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
}
