plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dt.docreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dt.docreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // POI / java.time 等在 Android 运行的前置条件
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // ===== P2 / P3 / P4 / P5 / P6 依赖：按需取消注释启用 =====
    // P2 代码高亮：
    // implementation(libs.sora.editor)
    // implementation(libs.sora.editor.lang.java)
    // P2 Markdown：
    // implementation(libs.markwon.core)
    // P3 PDF：
    // implementation(libs.pdfbox.android)
    // P4 Word：
    // implementation(libs.poi.ooxml)
    // P5 PPT：
    // implementation(libs.poi.scratchpad)
    // P6 Room：
    // implementation(libs.room.runtime)
    // implementation(libs.room.ktx)
    // ksp(libs.room.compiler)

    debugImplementation(libs.androidx.ui.tooling)
}
