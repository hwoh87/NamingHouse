plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.naminghouse.engine"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // kotlinx-datetime 은 JVM 에서 java.time 에 위임 — API 24-25 대응.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    // 사주 계산 코어(삼라 engine-core 에서 이식) — 유일한 외부 의존.
    api(libs.kotlinx.datetime)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
}
