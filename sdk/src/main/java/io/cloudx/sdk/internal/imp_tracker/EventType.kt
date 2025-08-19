package io.cloudx.sdk.internal.imp_tracker

enum class EventType(
    val pathSegment: String,
    val code: String,
) {
    SDK_INIT("sdkinitenc", "sdkinit"),
    CLICK("clickenc", "click"),
    IMPRESSION("sdkimpenc", "imp"),
    BID_REQUEST("bidreqenc", "bidreq"),
    SDK_ERROR("sdkerrorenc", "error"),
    SDK_METRICS("sdkmetricenc", "sdkmetricenc");

    companion object {
        fun from(code: String): EventType? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}