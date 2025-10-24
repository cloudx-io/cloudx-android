package io.cloudx.cd.staticrenderer

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View

internal fun createOnClickOnTouchCompatListener(onClick: () -> Unit): View.OnTouchListener =
    OnClickOnTouchCompat(onClick)

private class OnClickOnTouchCompat(private val onClick: () -> Unit): View.OnTouchListener {
    private var clickStarted = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                clickStarted = true
            }
            MotionEvent.ACTION_UP -> if (clickStarted) {
                clickStarted = false
                onClick()
            }
        }
        return false
    }
}