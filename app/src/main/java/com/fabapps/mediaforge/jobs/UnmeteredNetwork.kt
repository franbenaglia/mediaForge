package com.fabapps.mediaforge.jobs

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Si la red por defecto tiene internet y no es medida (Wi‑Fi o Ethernet, sin cobro por datos). */
@Singleton
class UnmeteredNetwork @Inject constructor(@ApplicationContext private val context: Context) {
    val available: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.isUnmetered())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(manager.getNetworkCapabilities(manager.activeNetwork)?.isUnmetered() == true)
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun NetworkCapabilities.isUnmetered() = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
