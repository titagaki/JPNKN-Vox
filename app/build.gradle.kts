import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// local.properties から署名情報を読み込む
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.github.titagaki.jpnknvox"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.github.titagaki.jpnknvox"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProperties.getProperty("KEYSTORE_FILE", "jpnknvox.jks"))
            storePassword = localProperties.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = localProperties.getProperty("KEY_ALIAS", "jpnknvox")
            keyPassword = localProperties.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

// APKファイル名をリネームするタスク
tasks.register("renameDebugApk") {
    dependsOn("assembleDebug")
    doLast {
        val debugApk = file("build/outputs/apk/debug/app-debug.apk")
        val renamedApk = file("build/outputs/apk/debug/JPNKNVox-debug-1.0.apk")
        if (debugApk.exists()) {
            debugApk.renameTo(renamedApk)
            println("APK renamed to: ${renamedApk.name}")
        }
    }
}

tasks.register("renameReleaseApk") {
    dependsOn("assembleRelease")
    doLast {
        val releaseApk = file("build/outputs/apk/release/app-release.apk")
        val renamedApk = file("build/outputs/apk/release/JPNKNVox-release-1.0.apk")
        if (releaseApk.exists()) {
            releaseApk.renameTo(renamedApk)
            println("APK renamed to: ${renamedApk.name}")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // WebSocket 通信用
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // MQTT 通信用 (HiveMQ - Android 14+ 対応)
    implementation("com.hivemq:hivemq-mqtt-client:1.3.3")

    // Reactive Streams (HiveMQ が使用)
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")

    // DataStore (設定の永続化)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}