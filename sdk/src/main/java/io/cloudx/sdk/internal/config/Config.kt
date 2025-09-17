package io.cloudx.sdk.internal.config

import android.os.Bundle
import io.cloudx.sdk.internal.AdNetwork
import io.cloudx.sdk.internal.config.Config.Placement
import org.json.JSONObject

/**
 * Config required for SDK initialization/startup: result of SDK Init API response.
 *
 * @property precacheSize - Max size of precached ad queue per placement
 * @property bidders - bid network's adapters participating in auction and ad precaching within CloudX SDK mediation.
 * @property placements - (key - [Placement.name]) ad placements
 */
internal class Config(
    val sessionId: String,
    val precacheSize: Int,
    val auctionEndpointUrl: EndpointConfig,
    val cdpEndpointUrl: EndpointConfig,
    val trackingEndpointUrl: String?,
    val geoDataEndpointUrl: String?,
    val winLossNotificationUrl: String?,
    val organizationId: String?,
    val appKeyOverride: String?,
    val accountId: String?,
    val bidders: Map<AdNetwork, Bidder>,
    val placements: Map<String, Placement>,
    val trackers: List<String>?,
    val winLossNotificationPayloadMapping: Map<String, String>,
    val geoHeaders: List<GeoHeader>?,
    val keyValuePaths: KeyValuePaths?,
    val metrics: MetricsConfig?,
    val rawJson: JSONObject?
) {

    /**
     * Bidder
     *
     * @property adNetwork - bid network name.
     * @property initData - bid network SDK's specific data required for its initializing
     */
    class Bidder(
        val adNetwork: AdNetwork,
        val initData: Bundle
    )

    /**
     * Placement data.
     * @property bidResponseTimeoutMillis
     * @property adLoadTimeoutMillis - timeout for ad load operation AFTER bid response returned
     */
    sealed class Placement(
        val id: String,
        val name: String,
        val bidResponseTimeoutMillis: Int,
        val adLoadTimeoutMillis: Int,
    ) {

        open class Banner(
            id: String,
            name: String,
            bidResponseTimeoutMillis: Int,
            adLoadTimeoutMillis: Int,
            val refreshRateMillis: Int,
            val hasCloseButton: Boolean = false,
        ) : Placement(
            id,
            name,
            bidResponseTimeoutMillis,
            adLoadTimeoutMillis,
        )

        class MREC(
            id: String,
            name: String,
            bidResponseTimeoutMillis: Int,
            adLoadTimeoutMillis: Int,
            refreshRateMillis: Int,
            hasCloseButton: Boolean = false
        ) : Banner(
            id,
            name,
            bidResponseTimeoutMillis,
            adLoadTimeoutMillis,
            refreshRateMillis,
            hasCloseButton
        )

        class Interstitial(
            id: String,
            name: String,
            bidResponseTimeoutMillis: Int,
            adLoadTimeoutMillis: Int,
        ) : Placement(id, name, bidResponseTimeoutMillis, adLoadTimeoutMillis)

        class Rewarded(
            id: String,
            name: String,
            bidResponseTimeoutMillis: Int,
            adLoadTimeoutMillis: Int,
        ) : Placement(id, name, bidResponseTimeoutMillis, adLoadTimeoutMillis)

        class Native(
            id: String,
            name: String,
            bidResponseTimeoutMillis: Int,
            adLoadTimeoutMillis: Int,
            val templateType: TemplateType,
            val refreshRateMillis: Int,
            val hasCloseButton: Boolean = false,
        ) : Placement(
            id,
            name,
            bidResponseTimeoutMillis,
            adLoadTimeoutMillis
        ) {

            sealed class TemplateType {
                data object Small : TemplateType()
                data object Medium : TemplateType()

                data class Unknown(val name: String) : TemplateType()
            }
        }
    }

    data class EndpointConfig(
        val default: String,
        val test: List<TestVariant>? = null
    ) {
        data class TestVariant(
            val name: String,
            val value: String,
            val ratio: Double
        )
    }

    data class GeoHeader(
        val source: String,
        val target: String
    )

    data class KeyValuePaths(
        val userKeyValues: String?,
        val appKeyValues: String?,
        val eids: String?,
        val placementLoopIndex: String?
    )

    data class MetricsConfig(
        val sendIntervalSeconds: Long = 60,
        val sdkApiCallsEnabled: Boolean? = null,
        val networkCallsEnabled: Boolean? = null,
        val networkCallsBidReqEnabled: Boolean? = null,
        val networkCallsInitSdkReqEnabled: Boolean? = null,
        val networkCallsGeoReqEnabled: Boolean? = null
    )
}