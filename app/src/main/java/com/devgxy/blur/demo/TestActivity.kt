package com.devgxy.blur.demo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat


/**
 * Created by center
 * 2021-12-06.
 */
class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test)
        setSystemUiState()

//        findViewById<Button>(R.id.button).setOnClickListener {
//            val view = findViewById<View>(R.id.blurview)
////            val width = view.width
////            val marginLayoutParams = view.layoutParams as MarginLayoutParams
////            marginLayoutParams.width = (width * .8).toInt()
////            view.layoutParams = marginLayoutParams
////            ValueAnimator.ofInt(0, 1).apply {
////                setDuration(500)
////                addUpdateListener {
////                    val marginLayoutParams = view.layoutParams as MarginLayoutParams
////                    marginLayoutParams.width = (width * (1 - animatedFraction * 0.8f)).toInt()
////                    view.layoutParams = marginLayoutParams
////                }
////            }.start()
//        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        setSystemUiState()
    }

    private fun setSystemUiState() {
        window.statusBarColor = Color.TRANSPARENT
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.isAppearanceLightStatusBars = false
    }


}