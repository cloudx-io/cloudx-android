package io.cloudx.sdk.internal.adapter

// TODO. Refactor. It shouldn't be implemented by AdFactory:
//  it should belong to Interstitial/whatever adapter.
interface CloudXAdapterMetaData {

    val sdkVersion: String
}

fun CloudXAdapterMetaData(sdkVersion: String): CloudXAdapterMetaData = CloudXAdapterMetaDataImpl(sdkVersion)

private class CloudXAdapterMetaDataImpl(override val sdkVersion: String) : CloudXAdapterMetaData