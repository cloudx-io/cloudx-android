package io.cloudx.sdk.internal.tracker.crash

import android.content.Context
import io.cloudx.sdk.internal.ApplicationContext
import io.cloudx.sdk.internal.config.Config
import io.cloudx.sdk.internal.tracker.EventTracker
import io.cloudx.sdk.internal.tracker.EventType
import io.cloudx.sdk.internal.tracker.TrackingFieldResolver
import io.cloudx.sdk.internal.tracker.XorEncryption
import org.json.JSONObject
import java.util.UUID

internal fun CrashReportingService(): CrashReportingService = LazySingleInstance

private val LazySingleInstance by lazy {
    CrashReportingService(
        context = ApplicationContext(),
        eventTracker = EventTracker()
    )
}

/**
 * Service responsible for handling and reporting SDK crashes
 */
internal class CrashReportingService(
    private val context: Context,
    private val eventTracker: EventTracker
) {

    private var basePayload: String = ""
    private var config: Config? = null

    fun setBasePayload(payload: String) {
        basePayload = payload
    }

    fun setConfig(config: Config?) {
        this.config = config
    }

    fun registerSdkCrashHandler() {
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler is SdkCrashHandler) return  // Only set if not already set by us
        Thread.setDefaultUncaughtExceptionHandler(
            SdkCrashHandler(currentHandler) { thread, throwable ->
                if (!isSdkRelatedCrash(throwable)) return@SdkCrashHandler
                config?.let {
                    val sessionId = it.sessionId
                    val errorMessage = throwable.message
                    val stackTrace = throwable.stackTraceToString()

                    val pendingReport = PendingCrashReport(
                        sessionId = sessionId,
                        errorMessage = errorMessage ?: "Unknown error",
                        errorDetails = stackTrace,
                        basePayload = basePayload,
                    )
                    savePendingCrashReport(pendingReport)
                }
            }
        )
    }

    fun sendPendingCrashIfAny() {
        val pending = getPendingCrashIfAny() ?: return
        sendCrashEvent(pending)
    }

    private fun getPendingCrashIfAny(): PendingCrashReport? {
        val prefs = context.getSharedPreferences("cloudx_crash_store", Context.MODE_PRIVATE)
        val pendingJson = prefs?.getString("pending_crash", null) ?: return null

        val pending = JSONObject(pendingJson).let { json ->
            PendingCrashReport(
                sessionId = json.getString("sessionId"),
                errorMessage = json.getString("errorMessage"),
                errorDetails = json.getString("errorDetails"),
                basePayload = json.getString("basePayload")
            )
        }

        prefs.edit().remove("pending_crash").commit()

        return pending
    }

    private fun sendCrashEvent(pendingCrashReport: PendingCrashReport) {
        val eventId = UUID.randomUUID().toString()

        var payload = if (pendingCrashReport.basePayload.isEmpty()) {
            basePayload.replace(ARG_PLACEHOLDER_EVENT_ID, eventId)
        } else {
            pendingCrashReport.basePayload.replace(ARG_PLACEHOLDER_EVENT_ID, eventId)
        }

        payload = payload.plus(";")
            .plus(pendingCrashReport.errorMessage).plus(";")
            .plus(pendingCrashReport.errorDetails)

        val accountId = TrackingFieldResolver.getAccountId()

        if (accountId != null) {
            val secret = XorEncryption.generateXorSecret(accountId)
            val campaignId = XorEncryption.generateCampaignIdBase64(accountId)
            val impressionId = XorEncryption.encrypt(payload, secret)
            eventTracker.send(impressionId, campaignId, "1", EventType.SDK_CRASH)
        }
    }

    private fun PendingCrashReport.toJson(): JSONObject {
        return JSONObject().apply {
            put("sessionId", sessionId)
            put("errorMessage", errorMessage)
            put("errorDetails", errorDetails)
            put("basePayload", basePayload)
        }
    }

    private fun savePendingCrashReport(report: PendingCrashReport) {
        val json = report.toJson().toString()
        val prefs = context.getSharedPreferences("cloudx_crash_store", Context.MODE_PRIVATE)
        prefs.edit().putString("pending_crash", json).commit()
    }

    private fun isSdkRelatedCrash(throwable: Throwable): Boolean {
        return throwable.stackTrace.any { it.className.startsWith("io.cloudx.sdk") }
    }

    data class PendingCrashReport(
        val sessionId: String,
        val errorMessage: String,
        val errorDetails: String,
        val basePayload: String
    )

    companion object {
        private const val ARG_PLACEHOLDER_EVENT_ID = "{eventId}"
    }
}