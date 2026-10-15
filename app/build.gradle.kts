plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.naminghouse.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.naminghouse.app"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 24
        targetSdk = 36
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
        // 전면 광고는 출시 기본값 OFF. 운영 단위와 활성 플래그를 모두 요구한다.
        resValue("string", "interstitial_ad_unit", providers.gradleProperty("INTERSTITIAL_AD_UNIT").orElse("").get())
        resValue("bool", "interstitial_ads_enabled", providers.gradleProperty("ENABLE_INTERSTITIAL_ADS").orElse("false").get())
        versionName = (project.findProperty("VERSION_NAME") as String?) ?: "1.0"
    }

    signingConfigs {
        create("release") {
            // 키는 저장소에 넣지 않는다 — 로컬 gradle.properties 나 CI 시크릿으로 주입한다.
            //   ./gradlew assembleRelease -PRELEASE_STORE_FILE=... -PRELEASE_STORE_PASSWORD=...
            val storeFilePath = project.findProperty("RELEASE_STORE_FILE") as String?
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            // 축소·난독화 켬. 에셋이 20MB 라 리소스 축소까지 해야 체감이 있다.
            isMinifyEnabled = true
            isShrinkResources = true
            // 서명 키가 없는 검토 환경에서도 unsigned 릴리스 빌드를 만들 수 있다.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time(API 26+)을 minSdk 24에서 안전하게.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

composeCompiler {
    // engine 모듈 모델은 compose 의존 없이 val-only data class — 여기서 안정 선언.
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.compose.runtime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.4")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
