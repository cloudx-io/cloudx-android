package io.cloudx.sdk.internal.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import io.cloudx.sdk.internal.CloudXLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal class GoogleFusedLocationProvider(
    private val context: Context
) : LocationProvider {

    private val TAG = "GoogleFusedLocationProvider"

    override suspend fun invoke() = getLocation()?.let {
        LocationProvider.Location(it.latitude.toFloat(), it.longitude.toFloat(), it.accuracy)
    }

    // Location permission: we do not check it nor request it explicitly. Simple try catch should do.
    @SuppressLint("MissingPermission")
    private suspend fun getLocation(): Location? = try {
        withContext(Dispatchers.IO) {
            suspendCoroutine { continuation ->
                var locationCallback: ((Task<Location?>) -> Unit)? = { task ->
                    continuation.resume(if (task.isSuccessful) task.result else null)
                }

                LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnCompleteListener {
                    val callback = locationCallback
                    // lastLocation.addOnCompleteListener() might call back multiple times sometimes.
                    // Such occurrence can crash suspendCoroutine.
                    // Don't want to use callbackFlow { } here.
                    locationCallback = null

                    callback?.invoke(it)
                }
            }
        }
    } catch (e: Exception) {
        CloudXLogger.d(TAG, e.toString())
        // Not enough location permissions, or location data simply unavailable.
        null
    }
}