package io.cloudx.sdk.internal.adapter

// Banners don't need it.
// Some ad network SDKs don't work with multiple ad instances/keys/whatever.
// For that case we need some sort of workaround.
// I could return some specific error in case if load() can't be executed properly,
// but then we might fire "load ad" tracking event which shouldn't be called in that case.
// So the use of this property is:
// if (isAdLoadOperationAvailable) load()
interface CloudXAdLoadOperationAvailability {

    val isAdLoadOperationAvailable: Boolean
}

object AlwaysReadyToLoadAd : CloudXAdLoadOperationAvailability {

    override val isAdLoadOperationAvailable = true
}