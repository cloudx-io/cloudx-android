package io.cloudx.sdk.internal.tracker.metrics

import com.xor.XorEncryption
import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.metrics.MetricsEvent
import io.cloudx.sdk.internal.tracker.EventType
import io.cloudx.sdk.internal.tracker.bulk.EventAM
import io.cloudx.sdk.internal.tracker.bulk.EventTrackerBulkApi
import io.cloudx.sdk.internal.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

internal class MetricsTrackerImpl(
    private val scope: CoroutineScope,
    private val eventTrackerBulkApi: EventTrackerBulkApi,
    private val db: CloudXDb
) : MetricsTracker {

    private var metricConfig: Config.MetricsConfig? = null
    private var sendInternalInSeconds: Long = 1000L

    private var endpoint: String? = null
    private var metricsSendJob: Job? = null
    private var sessionId: String = ""
    private var basePayload: String = ""
    private var accountId: String = ""

    override fun setBasicData(sessionId: String, accountId: String, basePayload: String) {
        this.sessionId = sessionId
        this.accountId = accountId
        this.basePayload = basePayload
    }

    override fun start(config: Config) {
        metricConfig = config.metrics
        if (metricConfig == null) {
            CXLogger.w("MetricsTrackerNewImpl", "Metrics configuration is null, skipping metrics tracking")
            return
        }

        endpoint = "${config.trackingEndpointUrl}/bulk?debug=true"
        sendInternalInSeconds = config.metrics?.sendIntervalSeconds ?: 60L

        CXLogger.d("MetricsTrackerNewImpl", "Starting metrics tracker with cycle duration: $sendInternalInSeconds seconds")
        metricsSendJob?.cancel()
        metricsSendJob = scope.launch {
            while (true) {
                delay(sendInternalInSeconds * 1000)
                trySendingPendingMetrics()
            }
        }
    }

    override fun stop() {
        metricsSendJob?.cancel()
        metricsSendJob = null
    }

    override fun trySendingPendingMetrics() {
        CXLogger.d("MetricsTrackerNewImpl", "Attempting to send pending metrics")
        scope.launch {
            val metrics = db.metricsEventDao().getAll()
            CXLogger.d("MetricsTrackerNewImpl", "Found ${metrics.size} pending metrics")
            if (metrics.isEmpty() || endpoint == null) return@launch

            val items = metrics.map { metric -> buildEvent(metric) }

            val result = eventTrackerBulkApi.send(endpoint!!, items)
            if (result is Result.Success) {
                // Delete metrics after successful send
                metrics.forEach { db.metricsEventDao().deleteById(it.id) }
            } else if (result is Result.Failure) {
                CXLogger.e(
                    "MetricsTrackerNewImpl", "Failed to send metrics: ${result.value.effectiveMessage}"
                )
            }
        }
    }

    override fun trackMethodCall(type: MetricsType.Method) {
        // Respect global and per-method call enablement flags
        val isMethodCallMetricsEnabled = metricConfig?.sdkApiCallsEnabled == true
        if (!isMethodCallMetricsEnabled) {
            CXLogger.w("MetricsTrackerNewImpl", "SDK API call metrics tracking is disabled for ${type.typeCode}")
            return
        }
        CXLogger.d("MetricsTrackerNewImpl", "Tracking SDK API call: ${type.typeCode}")
        trackMetric(type)
    }


    override fun trackNetworkCall(type: MetricsType.Network, latency: Long) {
        val isNetworkCallMetricsEnabled = metricConfig?.networkCallsEnabled == true
        val isCallMetricsEnabled = when (type) {
            MetricsType.Network.SdkInit -> metricConfig?.networkCallsInitSdkReqEnabled == true
            MetricsType.Network.GeoApi -> metricConfig?.networkCallsGeoReqEnabled == true
            MetricsType.Network.BidRequest -> metricConfig?.networkCallsBidReqEnabled == true
        }
        if (isNetworkCallMetricsEnabled && isCallMetricsEnabled) {
            CXLogger.d("MetricsTrackerNewImpl", "Tracking network request: ${type.typeCode} with latency: $latency ms")
            trackMetric(type, latency)
        } else {
            CXLogger.w("MetricsTrackerNewImpl", "Network call metrics tracking is disabled for ${type.typeCode}")
        }
    }

    private fun trackMetric(type: MetricsType, latency: Long = 0) {
        CXLogger.d("MetricsTrackerNewImpl", "Tracking metric: ${type.typeCode} with latency: $latency ms")
        scope.launch {
            val metricName = type.typeCode
            val existingMetric = db.metricsEventDao().getAllByMetric(metricName)
            CXLogger.d("MetricsTrackerNewImpl", "Existing metric for $metricName: $existingMetric")
            val updatedMetric = if (existingMetric == null) {
                CXLogger.d("MetricsTrackerNewImpl", "Creating new metric for $metricName")
                MetricsEvent(
                    id = UUID.randomUUID().toString(),
                    metricName = metricName,
                    counter = 1,
                    totalLatency = latency,
                    sessionId = sessionId,
                    auctionId = UUID.randomUUID().toString()
                )
            } else {
                CXLogger.d("MetricsTrackerNewImpl", "Updating existing metric for $metricName")
                existingMetric.apply {
                    counter += 1
                    totalLatency += latency
                }
            }
            db.metricsEventDao().insert(updatedMetric)
        }
    }

    // Build a single EventAM for one metric
    private fun buildEvent(metric: MetricsEvent): EventAM {
        val eventId = metric.auctionId
        val metricDetail = "${metric.counter}/${metric.totalLatency}"
        val payload = "$basePayload;${metric.metricName};$metricDetail".replace("{eventId}", eventId)
        CXLogger.d("MetricsTrackerNewImpl", "Building event for metric: ${metric.metricName} with payload: $payload")

        val secret = XorEncryption.generateXorSecret(accountId)
        val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
        val impressionId = XorEncryption.encrypt(payload, secret)

        return EventAM(
            impression = impressionId,
            campaignId = campaignId,
            eventValue = "N/A",
            eventName = EventType.SDK_METRICS.pathSegment,
            type = EventType.SDK_METRICS.pathSegment
        )
    }
}
