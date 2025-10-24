package io.cloudx.sdk.internal.tracker

enum class EventType(
    val pathSegment: String,
    val code: String,
) {
    SDK_INIT("sdkinitenc", "sdkinit"),
    CLICK("clickenc", "click"),
    IMPRESSION("sdkimpenc", "imp"),
    BID_REQUEST("bidreqenc", "bidreq"),
    SDK_ERROR("sdkerrorenc", "error"),
    SDK_CRASH("sdkcrashenc", "crash"),
    SDK_METRICS("sdkmetricenc", "sdkmetricenc");

    companion object {
        fun from(code: String): EventType? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}