package io.cloudx.sdk.internal.deviceinfo

import io.cloudx.sdk.internal.ApplicationContext

internal fun interface DeviceInfoProvider {
    suspend operator fun invoke(): DeviceInfo
}

internal class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val hwVersion: String,
    val isTablet: Boolean,
    val os: String,
    val osVersion: String,
    val apiLevel: Int,
    val language: String,
    val mobileCarrier: String,
    val screenDensity: Float,
)

internal fun DeviceInfoProvider(): DeviceInfoProvider = LazySingleInstance

private val LazySingleInstance by lazy {
    DeviceInfoServiceImpl(
        ApplicationContext()
    )
}