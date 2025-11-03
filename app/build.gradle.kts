import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.cookandroide.pikaboka"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cookandroide.pikaboka_v100_alpha"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "AZURE_SPEECH_KEY", "\"${localProperties["AZURE_SPEECH_KEY"]}\"")
        buildConfigField("String", "AZURE_SERVICE_REGION", "\"${localProperties["AZURE_SERVICE_REGION"]}\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"${localProperties["WEATHER_API_KEY"]}\"")
        buildConfigField("String", "ngrok", "\"${localProperties["ngrok"]}\"")
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
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
    // AndroidX 호환 버전 (compileSdk 34용)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Azure Speech SDK (최신 버전도 확인 권장)
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.33.0")

    // TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4") {
        exclude(group = "com.google.ai.edge.litert")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation("org.tensorflow:tensorflow-lite-api:2.17.0")

    // 테스트
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.11.0") // WebSocket
    implementation("com.microsoft.cognitiveservices.speech:client-sdk:1.30.0") // Azure Speech
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")

    // ExoPlayer
    implementation ("com.google.android.exoplayer:exoplayer:2.19.1")

    // Retrofit + Moshi
    implementation ("com.squareup.retrofit2:retrofit:2.11.0")
    implementation ("com.squareup.retrofit2:converter-moshi:2.11.0")

    // OkHttp 로깅
    implementation ("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation ("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation ("com.squareup.moshi:moshi:1.15.1")

    // Room
    implementation ("androidx.room:room-runtime:2.6.1")
    implementation ("androidx.room:room-ktx:2.6.1")
    kapt ("androidx.room:room-compiler:2.6.1")

    // Weather
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation ("com.squareup.okhttp3:okhttp:4.10.0")
    implementation ("org.json:json:20210307")

}
