import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

fun configString(name: String, environmentName: String): String =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(environmentName))
        .getOrElse("")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

val signingProperties = Properties()
val signingPropertiesFile = System.getenv("FACTORYBILL_SIGNING_PROPERTIES")
    ?.let { File(it) }
    ?.takeIf { it.isFile }
signingPropertiesFile?.inputStream()?.use(signingProperties::load)

android {
    namespace = "com.factorybill.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.factorybill.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CLOUD_SCAN_URL", "\"${configString("factoryBillCloudScanUrl", "FACTORYBILL_CLOUD_SCAN_URL")}\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${configString("factoryBillFirebaseProjectId", "FACTORYBILL_FIREBASE_PROJECT_ID")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${configString("factoryBillFirebaseAppId", "FACTORYBILL_FIREBASE_APP_ID")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${configString("factoryBillFirebaseApiKey", "FACTORYBILL_FIREBASE_API_KEY")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${configString("factoryBillFirebaseSenderId", "FACTORYBILL_FIREBASE_SENDER_ID")}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets { getByName("androidTest").assets.srcDir("$projectDir/schemas") }

    signingConfigs {
        if (signingPropertiesFile != null) {
            create("release") {
                storeFile = File(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (signingPropertiesFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Bundled (offline) Latin-script text recognition. Handwriting accuracy is limited.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta1")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
