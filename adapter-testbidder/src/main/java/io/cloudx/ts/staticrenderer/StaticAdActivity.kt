package io.cloudx.ts.staticrenderer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

// TODO. Consider single instance activity (not 1 activity per whole app) in AndroidManifest.
internal class StaticAdActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentActivity = this

        val webView = staticWebView ?: run {
            dismiss(); return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // Make sure WebView is visible and properly configured
        webView.visibility = VISIBLE
        webView.fitsSystemWindows = false
        root.addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val closeButton = AppCompatButton(this).apply {
            text = "Close"
            setOnClickListener {
                dismiss()
                finish()
            }
        }
        val closeLp =
            FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END)
        root.addView(closeButton, closeLp)

        // Capture initial padding/margins so we don't accumulate
        val startPadL = root.paddingLeft
        val startPadT = root.paddingTop
        val startPadR = root.paddingRight
        val startPadB = root.paddingBottom

        val startCloseTop = closeLp.topMargin
        val startCloseRight = closeLp.rightMargin

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                startPadL + bars.left,
                startPadT + bars.top,
                startPadR + bars.right,
                startPadB + bars.bottom
            )

            (closeButton.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = startCloseTop + bars.top
                rightMargin = startCloseRight + bars.right
            }.also { closeButton.layoutParams = it }

            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(root)
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                dismiss()
                finish()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        currentActivity = null
    }

    companion object {

        private var staticWebView: StaticWebView? = null

        private var currentActivity: StaticAdActivity? = null

        private val dismiss = MutableStateFlow(false)

        private fun dismiss() {
            dismiss.value = true
        }

        // Doesn't support parallel calls, not required though.
        suspend fun show(context: Context, staticWebView: StaticWebView) {
            try {
                Companion.staticWebView = staticWebView
                val intent = Intent(context, StaticAdActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                dismiss.first { it }

            } finally {
                dismiss.value = false
                currentActivity?.finish()
                Companion.staticWebView = null
            }
        }
    }
}