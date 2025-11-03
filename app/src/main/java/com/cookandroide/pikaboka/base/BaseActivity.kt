package com.cookandroide.pikaboka.base

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    private var isBackDisabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 공통 뒤로가기 제어 콜백
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isBackDisabled) {
                    // 정상 동작
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    // 비활성화 중
                    // 필요시 Toast 메시지 추가 가능
                    // Toast.makeText(this@BaseActivity, "뒤로가기 비활성화 중", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    // 외부에서 동적으로 제어
    fun setBackDisabled(disabled: Boolean) {
        isBackDisabled = disabled
    }

    protected fun addButtonClickEffect(button: View) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            false
        }
    }
}
