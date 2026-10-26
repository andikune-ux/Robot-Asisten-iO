package dev.andikune.robotasisten.io.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Continuous network state monitor via ConnectivityManager.NetworkCallback.
 */
class NetworkMonitor(context: Context) : AutoCloseable {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(NetworkHelper.isConnected(context))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    private val lock = Any()
    private val activeNetworks = mutableSetOf<Network>()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetwork(network)

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            updateNetwork(network, networkCapabilities)

        override fun onLost(network: Network) {
            synchronized(lock) {
                activeNetworks.remove(network)
                _isOnline.value = activeNetworks.isNotEmpty()
            }
        }
    }

    init {
        try {
            cm.allNetworks.forEach { updateNetwork(it) }
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback
            )
        } catch (e: Exception) {
            LogHelper.w("NetworkMonitor", "Could not register network callback: ${e.message}")
        }
    }

    private fun updateNetwork(network: Network, capabilities: NetworkCapabilities? = cm.getNetworkCapabilities(network)) {
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        synchronized(lock) {
            if (hasInternet) activeNetworks.add(network) else activeNetworks.remove(network)
            _isOnline.value = activeNetworks.isNotEmpty()
        }
    }

    override fun close() {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (e: IllegalArgumentException) {
            LogHelper.w("NetworkMonitor", "Network callback was already unregistered")
        }
    }
}
