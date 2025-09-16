package io.cloudx.sdk.internal.util

import android.content.Context

internal fun Context.dpToPx(dp: Int): Int =
    dpToPx(dp, resources?.displayMetrics?.density ?: 1f)

internal fun Context.pxToDp(px: Int): Int =
    pxToDp(px, resources?.displayMetrics?.density ?: 1f)

internal fun dpToPx(dp: Int, density: Float): Int = (dp * density).toInt()

internal fun pxToDp(px: Int, density: Float): Int = (px / density).toInt()

fun utcNowEpochMillis() = System.currentTimeMillis()
