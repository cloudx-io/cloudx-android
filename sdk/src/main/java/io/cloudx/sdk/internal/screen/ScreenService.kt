package io.cloudx.sdk.internal.screen

import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.cloudx.sdk.internal.util.pxToDp

// Main class
internal open class ScreenService(
    private val context: Context
) {

    open suspend operator fun invoke(): ScreenData {
        val windowManager = ContextCompat.getSystemService(context, WindowManager::class.java)!!

        return with(windowManager) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                fromApi30AndAbove(context)
            } else {
                legacy()
            }
        }
    }

    @RequiresApi(30)
    private fun WindowManager.fromApi30AndAbove(context: Context): ScreenData {
        val metrics = maximumWindowMetrics
        val bounds = metrics.bounds

        val resources = context.resources

        val densityDpi = resources.displayMetrics.densityDpi
        val density = resources.displayMetrics.density

        val wPx = bounds.width()
        val hPx = bounds.height()

        return ScreenData(
            wPx,
            hPx,
            pxToDp(wPx, density),
            pxToDp(hPx, density),
            densityDpi,
            density
        )
    }

    private fun WindowManager.legacy(): ScreenData {
        val dm = DisplayMetrics()
        defaultDisplay?.getRealMetrics(dm)

        return ScreenData(
            dm.widthPixels,
            dm.heightPixels,
            pxToDp(dm.widthPixels, dm.density),
            pxToDp(dm.heightPixels, dm.density),
            dm.densityDpi,
            dm.density
        )
    }

    class ScreenData(
        val widthPx: Int,
        val heightPx: Int,
        val widthDp: Int,
        val heightDp: Int,
        val dpi: Int,
        val pxRatio: Float
    )
}
