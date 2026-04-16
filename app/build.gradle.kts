plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // Kotlin 2.0+需要的Compose Compiler插件
    kotlin("plugin.serialization") version "2.0.21" // Serialization插件
}

android {
    namespace = "com.example.floatingscreencasting"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.floatingscreencasting"
        minSdk = 30
        targetSdk = 36
        versionCode = 100
        versionName = "0.1.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // Debug版本不需要后缀
        }
    }

    // 自定义APK输出文件名
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val versionName = "0.1.0-beta"
                val buildType = buildType.name
                output.outputFileName = "FSCast-${versionName}.apk"
            }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
        viewBinding = true  // 保留以支持渐进式迁移
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.compose.animation)

    // Compose Debug
    debugImplementation(libs.compose.ui.tooling)

    // Media3 ExoPlayer (视频播放)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer.hls)  // HLS支持（m3u8格式）
    implementation(libs.media3.datasource.okhttp)  // OkHttp数据源

    // OkHttp (用于视频缓存)
    implementation(libs.okhttp)

    // Gson
    implementation(libs.gson)

    // ConstraintLayout
    implementation(libs.constraintlayout)

    // EventBus
    implementation(libs.eventbus)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // NanoHTTPD (DLNA HTTP服务器)
    implementation(libs.nanohttpd)
    implementation(libs.nanohttpd.webserver)

    // Java-WebSocket (WebSocket服务器)
    implementation("org.java-websocket:Java-WebSocket:1.5.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}