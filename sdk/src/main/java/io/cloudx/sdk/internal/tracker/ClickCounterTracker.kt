package io.cloudx.sdk.internal.tracker

object ClickCounterTracker {

    private var counter: Map<String, Int> = emptyMap()

    fun incrementAndGet(auctionId: String): Int {
        val currentCount = counter[auctionId] ?: 0
        val newCount = currentCount + 1
        counter = counter + (auctionId to newCount)
        return newCount
    }

    fun reset() {
        counter = emptyMap()
    }

}