// Connectivity watcher for the offline pill — cached state stays visible,
// the pill just says so. 1:1 with the iOS NetworkMonitor.

package com.badmintonrallyup.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NetworkMonitor(context: Context) {
    var isOnline by mutableStateOf(true)
        private set

    init {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) {
                val active = cm.activeNetwork
                isOnline = active != null && active != network
            }
        })
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
