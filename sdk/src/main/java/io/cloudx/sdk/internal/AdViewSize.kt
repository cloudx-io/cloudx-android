package io.cloudx.sdk.internal

data class AdViewSize(val w: Int, val h: Int) {

    companion object {
        val Standard = AdViewSize(320, 50)
        val MREC = AdViewSize(300, 250)
    }
}