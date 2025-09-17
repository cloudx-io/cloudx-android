package io.cloudx.sdk

internal enum class CloudXErrorCode(
    val code: Int,
    val description: String
) {
    // --- Initialization (100–199)
    NOT_INITIALIZED(100, "SDK not initialized. Please initialize the SDK before using it."),
    INITIALIZATION_IN_PROGRESS(101, "SDK initialization is already in progress."),
    NO_ADAPTERS_FOUND(102, "No ad network adapters found. Please ensure adapters are properly integrated."),
    INITIALIZATION_TIMEOUT(103, "SDK initialization timed out."),
    INVALID_APP_KEY(104, "Invalid app key provided. Please check your app key."),
    SDK_DISABLED(105, "SDK is disabled by server configuration."),

    // --- Network (200–299)
    NETWORK_ERROR(200, "Network error occurred. Please check your internet connection."),
    NETWORK_TIMEOUT(201, "Network request timed out."),
    INVALID_RESPONSE(202, "Invalid response received from server."),
    SERVER_ERROR(203, "Server error occurred."),
    CLIENT_ERROR(204, "A client error occurred."),

    // --- Ad Loading (300–399)
    NO_FILL(300, "No ad available to show."),
    INVALID_REQUEST(301, "Invalid ad request parameters."),
    INVALID_PLACEMENT(302, "Invalid placement ID. Please check your placement configuration."),
    LOAD_TIMEOUT(303, "Ad loading timed out."),
    LOAD_FAILED(304, "Failed to load ad."),
    INVALID_AD(305, "Ad content is invalid or corrupted."),
    TOO_MANY_REQUESTS(306, "Too many ad requests. Please reduce request frequency."),
    REQUEST_CANCELLED(307, "Ad request was cancelled."),
    ADS_DISABLED(308, "Ads are disabled by server configuration."),

    // --- Ad Display (400–499)
    AD_NOT_READY(400, "Ad is not ready to be displayed."),
    AD_ALREADY_SHOWN(401, "Ad has already been shown."),
    AD_EXPIRED(402, "Ad has expired and cannot be shown."),
    INVALID_VIEW_CONTROLLER(403, "Invalid view controller provided for ad display."),
    SHOW_FAILED(404, "Failed to show ad."),

    // --- Configuration (500–599)
    INVALID_AD_UNIT(500, "Invalid ad unit configuration."),
    PERMISSION_DENIED(501, "Required permissions not granted."),
    UNSUPPORTED_AD_FORMAT(502, "Ad format not supported."),
    INVALID_BANNER_VIEW(503, "Banner view is nil or invalid."),
    INVALID_NATIVE_VIEW(504, "Native view is nil or invalid."),

    // --- General (600–699)
    UNEXPECTED_ERROR(600, "An unexpected error occurred.")
}