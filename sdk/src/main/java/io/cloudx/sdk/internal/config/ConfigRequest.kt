package io.cloudx.sdk.internal.config

/**
 * Config request data required for SDK initialization/startup (initial configuration request)
 *
 * @property bundle - app package name, SDK is integrated into
 * @property sdkVersion - version of SDK.
 * @property gaid - Google Advertising Id.
 * @property dnt - Google's Do Not Track flag.
 */
internal class ConfigRequest(
    val bundle: String,
    val os: String,
    val osVersion: String,
    val deviceModel: String,
    val deviceManufacturer: String,
    val sdkVersion: String,
    val gaid: String,
    val dnt: Boolean,
)