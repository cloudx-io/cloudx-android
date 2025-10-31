package io.cloudx.sdk.internal.tracker.metrics

import io.cloudx.sdk.internal.CXLogger
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.db.CloudXDb
import io.cloudx.sdk.internal.db.Database
import io.cloudx.sdk.internal.db.metrics.MetricsEvent
import io.cloudx.sdk.internal.tracker.EventType
import io.cloudx.sdk.internal.tracker.XorEncryption
import io.cloudx.sdk.internal.tracker.bulk.EventAM
import io.cloudx.sdk.internal.tracker.bulk.EventTrackerBulkApi
import io.cloudx.sdk.internal.util.Result
import io.cloudx.sdk.internal.util.ThreadUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

// Lazy singleton
private val LazySingleInstance by lazy {
    MetricsTracker(
        ThreadUtils.GlobalIOScope,
        EventTrackerBulkApi(),
        Database()
    )
}

// Factory function
internal fun MetricsTracker(): MetricsTracker = LazySingleInstance

// Main class
internal class MetricsTracker(
    private val scope: CoroutineScope,
    private val eventTrackerBulkApi: EventTrackerBulkApi,
    private val db: CloudXDb
) {

    private val logger = CXLogger.forComponent("MetricsTracker")
    private var metricConfig: Config.MetricsConfig? = null
    private var sendInternalInSeconds: Long = 1000L

    private var endpoint: String? = null
    private var metricsSendJob: Job? = null
    private var sessionId: String = ""
    private var basePayload: String = ""
    private var accountId: String = ""

    fun setBasicData(sessionId: String, accountId: String, basePayload: String) {
        this.sessionId = sessionId
        this.accountId = accountId
        this.basePayload = basePayload
    }

    fun start(config: Config) {
        metricConfig = config.metrics
        if (metricConfig == null) {
            logger.w("Metrics configuration is null, skipping metrics tracking")
            return
        }

        endpoint = "${config.trackingEndpointUrl}/bulk"
        sendInternalInSeconds = config.metrics?.sendIntervalSeconds ?: 60L

        logger.d("Starting metrics tracker with cycle duration: $sendInternalInSeconds seconds")
        metricsSendJob?.cancel()
        metricsSendJob = scope.launch {
            while (true) {
                delay(sendInternalInSeconds * 1000)
                trySendingPendingMetrics()
            }
        }
    }

    fun stop() {
        metricsSendJob?.cancel()
        metricsSendJob = null
    }

    fun trySendingPendingMetrics() {
        logger.d("Attempting to send pending metrics")
        scope.launch {
            val metrics = db.metricsEventDao().getAll()
            logger.d("Found ${metrics.size} pending metrics")
            if (metrics.isEmpty() || endpoint == null) return@launch

            val items = metrics.map { metric -> buildEvent(metric) }

            val result = eventTrackerBulkApi.send(endpoint!!, items)
            if (result is Result.Success) {
                // Delete metrics after successful send
                metrics.forEach { db.metricsEventDao().deleteById(it.id) }
            } else if (result is Result.Failure) {
                logger.e("Failed to send metrics: ${result.value.effectiveMessage}")
            }
        }
    }

    fun trackMethodCall(type: MetricsType.Method) {
        // Respect global and per-method call enablement flags
        val isMethodCallMetricsEnabled = metricConfig?.sdkApiCallsEnabled == true
        if (!isMethodCallMetricsEnabled) {
            logger.w("SDK API call metrics tracking is disabled for ${type.typeCode}")
            return
        }
        logger.d("Tracking SDK API call: ${type.typeCode}")
        trackMetric(type)
    }


    fun trackNetworkCall(type: MetricsType.Network, latency: Long) {
        val isNetworkCallMetricsEnabled = metricConfig?.networkCallsEnabled == true
        val isCallMetricsEnabled = when (type) {
            MetricsType.Network.SdkInit -> metricConfig?.networkCallsInitSdkReqEnabled == true
            MetricsType.Network.GeoApi -> metricConfig?.networkCallsGeoReqEnabled == true
            MetricsType.Network.BidRequest -> metricConfig?.networkCallsBidReqEnabled == true
        }
        if (isNetworkCallMetricsEnabled && isCallMetricsEnabled) {
            logger.d("Tracking network request: ${type.typeCode} with latency: $latency ms")
            trackMetric(type, latency)
        } else {
            logger.w("Network call metrics tracking is disabled for ${type.typeCode}")
        }
    }

    private fun trackMetric(type: MetricsType, latency: Long = 0) {
        logger.d("Tracking metric: ${type.typeCode} with latency: $latency ms")
        scope.launch {
            val metricName = type.typeCode
            val existingMetric = db.metricsEventDao().getAllByMetric(metricName)
            logger.d("Existing metric for $metricName: $existingMetric")
            val updatedMetric = if (existingMetric == null) {
                logger.d("Creating new metric for $metricName")
                MetricsEvent(
                    id = UUID.randomUUID().toString(),
                    metricName = metricName,
                    counter = 1,
                    totalLatency = latency,
                    sessionId = sessionId,
                    auctionId = UUID.randomUUID().toString()
                )
            } else {
                logger.d("Updating existing metric for $metricName")
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
        logger.d("Building event for metric: ${metric.metricName} with payload: $payload")

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
