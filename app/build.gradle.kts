plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.catinder"
    compileSdk = 34

    // NDK с поддержкой 16k-страниц (см. target_link_options в CMakeLists).
    ndkVersion = "27.3.13750724"

    buildFeatures {
        buildConfig = true
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    defaultConfig {
        // Сервер хостится на VM: https, стандартный порт (Let's Encrypt).
        buildConfigField("String", "BASE_URL", "\"https://mobius2026.sk1talec.ru\"")
        applicationId = "com.catinder"
        resValue("string", "app_name", "Catinder")
        resValue("color", "ic_launcher_foreground_fill", "#FFFFFF")
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // 16k-устройства требуют выравнивания несжатых .so в APK по 16 КБ; AGP 8.7
    // в прямой сборке APK не выравнивает — кладём jniLibs сжатыми (распаковка
    // при установке), требование смещения не применяется.
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Нативный валидатор JPEG (catcodec), собирается CMake из исходника
    // в src/main/cpp.
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // opentelemetry-api — compileOnly: классы API в рантайме могут
    // отсутствовать; вызовы защищены, без реализации заголовки просто
    // не отправляются.
    compileOnly("io.opentelemetry:opentelemetry-api:1.24.0")
}
