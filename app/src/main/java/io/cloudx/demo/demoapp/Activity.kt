package io.cloudx.demo.demoapp

import android.app.Activity
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

fun Activity.shortToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

fun Activity.shortSnackbar(contextView: View, text: String) {
    Snackbar.make(contextView, text, Snackbar.LENGTH_SHORT).apply {
        anchorView = contextView
    }.show()
}

// Utility for attaching to flows once view is initialized and attached to the screen.
fun LifecycleOwner.repeatOnStart(block: suspend () -> Unit) {
    lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            block()
        }
    }
}