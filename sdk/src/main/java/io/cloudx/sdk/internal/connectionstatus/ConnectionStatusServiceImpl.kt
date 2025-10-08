package io.cloudx.sdk.internal.connectionstatus

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.net.ConnectivityManagerCompat
import io.cloudx.sdk.internal.CXLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private val connectionStatusLogger = CXLogger.forComponent("ConnectionStatusService")

internal class ConnectionStatusServiceImpl(context: Context) : ConnectionStatusService {

    override val currentConnectionInfoEvent: Flow<ConnectionInfo?> = flow {
        while (true) {
            emit(context.connectionInfo())
            delay(1000)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    override suspend fun currentConnectionInfo(): ConnectionInfo? =
        currentConnectionInfoEvent.first()

    override suspend fun awaitConnection(): ConnectionInfo =
        currentConnectionInfoEvent.first { it != null } as ConnectionInfo
}

private fun Context.connectionInfo(): ConnectionInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        modernConnectionInfo()
    } else {
        preApi29ConnectionInfo()
    }

// Already added non-dangerous android.permission.READ_BASIC_PHONE_STATE permission;
// don't want to pollute with unnecessary permission check code, hence @SupressLint("MissingPermission")
@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.modernConnectionInfo(): ConnectionInfo? = try {
    connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let { nc ->
        ConnectionInfo(
            isMetered = ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager),
            when {
                nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
                nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.Ethernet
                nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> mobileConnectionType(
                    telephonyManager.dataNetworkType
                )

                else -> ConnectionType.Unknown
            }
        )
    }
} catch (e: Exception) {
    connectionStatusLogger.e(e.toString(), e)
    null
}

private fun Context.preApi29ConnectionInfo(): ConnectionInfo? = try {
    connectivityManager.activeNetworkInfo?.let { networkInfo ->
        ConnectionInfo(
            isMetered = ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager),
            type = when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> ConnectionType.WIFI
                ConnectivityManager.TYPE_ETHERNET -> ConnectionType.Ethernet
                ConnectivityManager.TYPE_MOBILE -> mobileConnectionType(networkInfo.subtype)
                else -> ConnectionType.Unknown
            }
        )
    }
} catch (e: Exception) {
    connectionStatusLogger.e(e.toString(), e)
    null
}

private val Context.connectivityManager: ConnectivityManager
    get() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

private val Context.telephonyManager: TelephonyManager
    get() = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

private fun mobileConnectionType(type: Int) = when (type) {
    TelephonyManager.NETWORK_TYPE_GPRS,
    TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_CDMA,
    TelephonyManager.NETWORK_TYPE_1xRTT,
    TelephonyManager.NETWORK_TYPE_IDEN -> ConnectionType.Mobile2g

    TelephonyManager.NETWORK_TYPE_UMTS,
    TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_EVDO_A,
    TelephonyManager.NETWORK_TYPE_HSDPA,
    TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_EVDO_B,
    TelephonyManager.NETWORK_TYPE_EHRPD,
    TelephonyManager.NETWORK_TYPE_HSPAP -> ConnectionType.Mobile3g

    TelephonyManager.NETWORK_TYPE_LTE -> ConnectionType.Mobile4g
    TelephonyManager.NETWORK_TYPE_NR -> ConnectionType.Mobile5g

    else -> ConnectionType.MobileUnknown
}