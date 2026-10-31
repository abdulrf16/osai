package com.fronx.smartlauncher.connectivity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class RadioState { UNAVAILABLE, OFF, ON, CONNECTED }

/** Real, publicly readable state only. Bluetooth reports ON/OFF (peers need a runtime permission we do not request). */
data class ConnectivityState(
    val bluetooth: RadioState = RadioState.UNAVAILABLE,
    val wifi: RadioState = RadioState.UNAVAILABLE,
    val hasInternet: Boolean = false
)

class ConnectivityMonitor(context: Context) {
    private val app = context.applicationContext
    private val cm = ContextCompat.getSystemService(app, ConnectivityManager::class.java)
    private val wm = ContextCompat.getSystemService(app, WifiManager::class.java)
    private val bt: BluetoothAdapter? = ContextCompat.getSystemService(app, BluetoothManager::class.java)?.adapter

    @Suppress("DEPRECATION")
    fun snapshot(): ConnectivityState {
        var onWifiTransport = false
        var internet = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val caps = runCatching { cm?.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            onWifiTransport = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            internet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            val info = runCatching { cm?.activeNetworkInfo }.getOrNull()
            internet = info?.isConnected == true
            onWifiTransport = internet && info?.type == ConnectivityManager.TYPE_WIFI
        }
        val wifi = when {
            wm == null -> RadioState.UNAVAILABLE
            !runCatching { wm?.isWifiEnabled == true }.getOrDefault(false) -> RadioState.OFF
            onWifiTransport -> RadioState.CONNECTED
            else -> RadioState.ON
        }
        val btState = when {
            bt == null -> RadioState.UNAVAILABLE
            runCatching { bt?.isEnabled == true }.getOrDefault(false) -> RadioState.ON
            else -> RadioState.OFF
        }
        return ConnectivityState(btState, wifi, internet)
    }

    fun isOnline(): Boolean = snapshot().hasInternet

    /** Event-driven (broadcasts + network callback); collected only while the UI is visible. */
    val state: Flow<ConnectivityState> = callbackFlow {
        trySend(snapshot())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { trySend(snapshot()) }
        }
        app.registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        })
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(snapshot()) }
            override fun onLost(network: Network) { trySend(snapshot()) }
            override fun onCapabilitiesChanged(network: Network, nc: NetworkCapabilities) { trySend(snapshot()) }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cm?.registerDefaultNetworkCallback(cb)
            else cm?.registerNetworkCallback(NetworkRequest.Builder().build(), cb)
        }
        awaitClose {
            runCatching { app.unregisterReceiver(receiver) }
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
    }.distinctUntilChanged()
}
