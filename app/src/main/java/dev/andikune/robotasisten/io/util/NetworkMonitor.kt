package dev.andikune.robotasisten.io.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Continuous network state monitor via ConnectivityManager.NetworkCallback.
 */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _isOnline = MutableStateFlow(NetworkHelper.isConnected(context))
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        try {
            val builder = NetworkRequest.Builder()
            cm.registerNetworkCallback(builder.build(), object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                }
            })
        } catch (e: Exception) {
            LogHelper.w("NetworkMonitor", "Could not register network callback: ${e.message}")
        }
    }
}
