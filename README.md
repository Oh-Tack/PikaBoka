! AZURE_SPEECH_KEY, AZURE_SERVICE_REGION 값 기재 !

// TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4") {
        exclude(group = "com.google.ai.edge.litert", module = "litert")
        exclude(group = "com.google.ai.edge.litert", module = "litert-api")
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
