plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.cookandroide.pikaboka"
    compileSdk = 34  // 최신 AndroidX 라이브러리 호환 위해 34~35 가능

    defaultConfig {
        applicationId = "com.cookandroide.pikaboka"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Azure Speech SDK
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.33.0")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4") {
        exclude(group = "com.google.ai.edge.litert", module = "litert")
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
}
