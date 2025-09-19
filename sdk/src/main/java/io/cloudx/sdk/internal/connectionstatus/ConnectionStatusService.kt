package io.cloudx.sdk.internal.connectionstatus

import io.cloudx.sdk.internal.ApplicationContext
import kotlinx.coroutines.flow.Flow

/**
 * Connection status service - provides information about current internet connection, it's type etc
 */
internal interface ConnectionStatusService {

    /**
     * Await connection: suspends until internet connection is established
     * @return [ConnectionInfo] of active connection
     */
    suspend fun awaitConnection(): ConnectionInfo

    /**
     * Current connection info
     *
     * @return null - no active connections (no internet)
     */
    suspend fun currentConnectionInfo(): ConnectionInfo?

    val currentConnectionInfoEvent: Flow<ConnectionInfo?>
}

internal data class ConnectionInfo(val isMetered: Boolean, val type: ConnectionType)

internal enum class ConnectionType {
    Mobile2g, Mobile3g, Mobile4g, Mobile5g, MobileUnknown, WIFI, Ethernet, Unknown
}

internal fun ConnectionStatusService(): ConnectionStatusService = LazySingleInstance

private val LazySingleInstance by lazy {
    ConnectionStatusServiceImpl(
        ApplicationContext()
    )
}