package io.cloudx.sdk.internal

/**
 * List of supported Ad networks.
 */
internal sealed class AdNetwork(val networkName: String) {
    data object GoogleAdManager : AdNetwork("GoogleAdManager")
    data object Meta : AdNetwork("Meta")
    data object Mintegral : AdNetwork("Mintegral")
    data object CloudX : AdNetwork("CloudX")
    data object CloudXSecond : AdNetwork("CloudX2")
    data class Unknown(val name: String) : AdNetwork(name)
}

/**
 * [AdNetwork] mapper for network name strings coming from back-end (Config, Bidding APIs etc)
 */
internal fun String.toAdNetwork(): AdNetwork = when (this) {
    "googleAdManager" -> AdNetwork.GoogleAdManager
    "meta" -> AdNetwork.Meta
    "mintegral" -> AdNetwork.Mintegral
    "testbidder", "cloudx" -> AdNetwork.CloudX
    "cloudxsecond" -> AdNetwork.CloudXSecond
    else -> AdNetwork.Unknown(name = this)
}

internal fun AdNetwork.toAdapterPackagePrefix(): String? = when (this) {
    AdNetwork.GoogleAdManager -> "googleadmanager"
    AdNetwork.Meta -> "meta"
    AdNetwork.Mintegral -> "mintegral"
    AdNetwork.CloudX -> "cloudx"
    AdNetwork.CloudXSecond -> "cloudx"
    is AdNetwork.Unknown -> null
}?.let {
    "io.cloudx.adapter.$it."
}

internal fun AdNetwork.toBidRequestString() = when (this) {
    AdNetwork.GoogleAdManager -> "googleAdManager"
    AdNetwork.Meta -> "meta"
    AdNetwork.Mintegral -> "mintegral"
    AdNetwork.CloudX -> "cloudx"
    AdNetwork.CloudXSecond -> "cloudxsecond"
    is AdNetwork.Unknown -> this.name
}
