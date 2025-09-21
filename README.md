# PikaBoka

**PikaBoka**는 일본어 학습자를 위한 안드로이드 앱입니다.  
주요 기능:
- 🎤 **발음 평가**: Azure Speech Service를 이용해 발음을 평가하고 점수를 제공합니다.  
- ✍ **손글씨 평가**: 사용자가 직접 쓴 히라가나를 TensorFlow Lite 모델로 판별합니다.  

---

## 기능

### 발음 평가 (SpeechActivity)
- 마이크로 문장을 입력하면 발음 정확도/억양 점수를 계산.  
- Azure Speech SDK 필요.  

### 손글씨 평가 (HandwritingActivity)
- `DrawingView` 캔버스에서 히라가나를 직접 작성.  
- `k49_cnn.tflite` 모델을 사용하여 입력된 글자를 분류.  
- 랜덤으로 문제 제시 → 정답 여부와 신뢰도 출력.  

---

## 실행 방법

1. **Azure Speech 설정**
   - `SpeechActivity.kt` 내부의 키/리전 값을 본인 Azure 계정 정보로 교체:
     ```kotlin
     private val AZURE_SPEECH_KEY = "YOUR_KEY"
     private val AZURE_SERVICE_REGION = "YOUR_REGION"
     ```

2. **TFLite 모델 추가**
   - `app/src/main/assets/` 경로에 `k49_cnn.tflite` 파일 배치.  

3. **의존성**
   `build.gradle`에 다음 의존성이 포함되어야 합니다:
   ```gradle
   implementation 'com.microsoft.cognitiveservices.speech:client-sdk:1.34.0'
   implementation 'org.tensorflow:tensorflow-lite:2.14.0'

4. **권한**
    앱 실행 시 마이크 권한(RECORD_AUDIO) 허용 필요.

**주요 파일 설명**
    *Kotlin 파일*
        MainActivity.kt
        앱 메인 화면. 발음 평가와 손글씨 평가 메뉴로 이동하는 버튼 제공.

        SpeechActivity.kt
        Azure Speech SDK를 이용해 발음 평가 수행. 정확도·억양 점수를 반환하고 UI에 표시.

        HandwritingActivity.kt
        사용자가 쓴 글자를 DrawingView에서 가져와 28×28로 전처리 후 TFLite 모델로 분류. 결과를 정답/오답으로 표시.

        DrawingView.kt
        손글씨 입력을 위한 커스텀 뷰. Path를 비트맵에 기록하고 28×28 float 배열로 변환하는 기능 제공.

        BaseActivity.kt
        공통 기능 제공 (버튼 클릭 시 애니메이션 효과).

    *레이아웃 파일*
        activity_main.xml
        앱 타이틀과 발음 평가 / 손글씨 평가 버튼 UI.

        activity_speech.xml
        발음 평가 화면 UI. 인식 결과 텍스트와 발음 시작 버튼, 뒤로 가기 버튼 포함.

        activity_handwriting.xml
        손글씨 평가 화면 UI. 문제 표시, 캔버스 영역(DrawingView), 평가/지우기/다음 문제/뒤로가기 버튼 포함.

**프로젝트 구조**
    app/src/main/java/com/cookandroide/pikaboka/
    ├─ MainActivity.kt
    ├─ SpeechActivity.kt
    ├─ HandwritingActivity.kt
    ├─ BaseActivity.kt
    └─ views/DrawingView.kt

    app/src/main/res/layout/
    ├─ activity_main.xml
    ├─ activity_speech.xml
    └─ activity_handwriting.xml