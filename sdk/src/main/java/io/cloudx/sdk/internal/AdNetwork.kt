package io.cloudx.sdk.internal

/**
 * List of supported Ad networks.
 */
internal sealed class AdNetwork(val networkName: String) {

    data object TestNetwork : AdNetwork("TestNetwork")
    data object GoogleAdManager : AdNetwork("GoogleAdManager")
    data object Meta : AdNetwork("Meta")
    data object Mintegral : AdNetwork("Mintegral")
    data object CloudX : AdNetwork("CloudX")
    data object CloudXSecond : AdNetwork("CloudX2")
    data class Unknown(val name: String) : AdNetwork(name)
}

/**
 * String representation of [AdNetwork] for Public APIs.
 */
internal fun AdNetwork?.toPublisherNetworkString(): String = 
    this?.networkName ?: ""

/**
 * [AdNetwork] mapper for network name strings coming from back-end (Config, Bidding APIs etc)
 */
internal fun String.toAdNetwork(): AdNetwork = when (this) {
    "testbidder" -> AdNetwork.TestNetwork
    "googleAdManager" -> AdNetwork.GoogleAdManager
    "meta" -> AdNetwork.Meta
    "mintegral" -> AdNetwork.Mintegral
    "cloudx" -> AdNetwork.CloudX
    "cloudxsecond" -> AdNetwork.CloudXSecond
    else -> AdNetwork.Unknown(name = this)
}