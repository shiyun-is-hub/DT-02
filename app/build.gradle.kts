import java.util.Properties

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
        versionCode = 6
        versionName = "0.6.0"
        multiDexEnabled = true
    }

    /**
     * 正式签名。
     *
     * 密钥库位置与口令从 `keystore.properties` 读取（该文件**不入库**，
     * 见 .gitignore），避免把密钥/口令提交到公开仓库。
     * 若该文件不存在（如 CI 或他人克隆），则回退到 debug 签名，保证仍可构建。
     */
    val keystoreProps = Properties()
    run {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { keystoreProps.load(it) }
    }
    val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Debug 也开启代码压缩与资源裁剪 —— 体积优化关键。
            // 用 R8 full mode 裁掉未用的 Compose/AndroidX 代码。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // debug 包保留可调试性（不破坏调试体验）
            isDebuggable = true
            // 正式签名：使 debug 包也能覆盖安装已签名的正式包
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // 体积优化：只保留 arm64-v8a（本项目面向 arm64 手机；如需兼容 32 位可加回 armeabi-v7a）
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
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
        // 设置页需要读 BuildConfig.VERSION_NAME 动态显示版本号
        buildConfig = true
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    // 注意：已移除 material-icons-extended（体积巨大）与 navigation-compose（已用状态机替代），
    //       全部图标改为 Tabler Icons 转 VectorDrawable。

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

    // ===== 单元测试（审计要求：纯逻辑层必须有测试）=====
    testImplementation("junit:junit:4.13.2")
}