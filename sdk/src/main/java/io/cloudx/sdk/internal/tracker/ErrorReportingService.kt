package io.cloudx.sdk.internal.tracker

import java.util.UUID

internal fun ErrorReportingService(): ErrorReportingService = LazySingleInstance

private val LazySingleInstance by lazy {
    ErrorReportingService(
        eventTracker = EventTracker()
    )
}

/**
 * Service responsible for reporting SDK errors (non-crash errors)
 */
internal class ErrorReportingService(
    private val eventTracker: EventTracker
) {

    private var basePayload: String = ""

    fun setBasePayload(payload: String) {
        basePayload = payload
    }

    fun sendErrorEvent(errorMessage: String, errorDetails: String = "") {
        val eventId = UUID.randomUUID().toString()

        var payload = basePayload.replace(ARG_PLACEHOLDER_EVENT_ID, eventId)

        payload = payload.plus(";")
            .plus(errorMessage).plus(";")
            .plus(errorDetails)

        val accountId = TrackingFieldResolver.getAccountId()

        if (accountId != null) {
            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            eventTracker.send(impressionId, campaignId, "1", EventType.SDK_ERROR)
        }
    }

    companion object {
        private const val ARG_PLACEHOLDER_EVENT_ID = "{eventId}"
    }
}
