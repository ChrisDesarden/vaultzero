package com.vaultzero.app.presentation.base

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity

/**
 * Base activity that hardens the app window against common surface-level attacks:
 *  - Blocks screenshots and screen recordings (FLAG_SECURE)
 *  - Rejects touch events when the window is obscured by another app (tapjacking)
 *  - Blanks the app thumbnail in recent apps (FLAG_SECURE side effect)
 */
abstract class SecureActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hardenWindow()
    }

    private fun hardenWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Tapjacking: drop touches that reach us through another app's overlay.
        window.decorView.apply {
            filterTouchesWhenObscured = true
            setOnTouchListener { _, event ->
                if (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED != 0 ||
                    event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED != 0
                ) {
                    return@setOnTouchListener true
                }
                false
            }
        }
    }
}
