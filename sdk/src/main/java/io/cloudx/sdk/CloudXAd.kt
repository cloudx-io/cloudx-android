package io.cloudx.sdk

interface CloudXAd {
    val placementName: String
    val placementId: String
    val bidderName: String
    val externalPlacementId: String?
    val revenue: Double
}