package org.nan0s.vm

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** Android owns the physical radios. NaNNet consumes the default route; it is not a PCI device. */
object WifiBridge {
    private fun wifi(c: Context): WifiManager =
        c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun hasPermission(c: Context): Boolean {
        val fine = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearby = Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        return fine && nearby
    }

    fun requestPermission(a: Activity, code: Int) {
        val ask = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        if (Build.VERSION.SDK_INT >= 33) ask.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        a.requestPermissions(ask.toTypedArray(), code)
    }

    @Suppress("DEPRECATION")
    fun status(c: Context): String {
        val w = wifi(c)
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = if (active != null) cm.getNetworkCapabilities(active) else null
        val info = w.connectionInfo
        val connectedWifi = w.isWifiEnabled && info.networkId >= 0
        val ssid = info.ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "SSID hidden"
        val sb = StringBuilder("NANNET • ANDROID HOST NETWORK MANAGER\n")
        sb.append("Wi-Fi radio: ").append(if (w.isWifiEnabled) "ON" else "OFF")
        if (connectedWifi) {
            sb.append("\nWi-Fi SSID: ").append(ssid)
            sb.append("\nWi-Fi signal: ").append(info.rssi).append(" dBm")
            if (info.linkSpeed > 0) sb.append(" • ").append(info.linkSpeed).append(" Mbps link")
        } else sb.append("\nWi-Fi association: not connected")
        sb.append("\nActive route: ")
        when {
            caps == null -> sb.append("OFFLINE / no default network")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> sb.append("WI-FI")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> sb.append("CELLULAR (LTE/5G selected by Android)")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> sb.append("ETHERNET")
            else -> sb.append("ANDROID NETWORK")
        }
        if (caps != null) {
            sb.append("\nInternet: ").append(if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "available" else "local only")
            sb.append("\nValidated: ").append(if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "yes" else "not verified")
            if (Build.VERSION.SDK_INT >= 29 && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                sb.append("\nCellular downstream: ").append(caps.linkDownstreamBandwidthKbps).append(" kbps")
        }
        sb.append("\nNaNNet adapter: Android ConnectivityManager (no guest PCI device)")
        sb.append("\nGateway traffic uses the active Wi-Fi OR cellular data route.")
        return sb.toString()
    }

    data class Ap(val ssid: String, val level: Int, val security: String)

    @Suppress("DEPRECATION")
    fun scan(c: Context, done: (List<Ap>) -> Unit) {
        val app = c.applicationContext
        val w = wifi(app)
        if (!w.isWifiEnabled || !locationEnabled(app)) { done(emptyList()); return }
        val handler = Handler(Looper.getMainLooper())
        var completed = false
        var receiver: BroadcastReceiver? = null
        fun deliver() {
            if (completed) return
            completed = true
            try { receiver?.let { app.unregisterReceiver(it) } } catch (_: Exception) { }
            val list = try {
                w.scanResults.asSequence().filter { it.SSID.isNotBlank() && it.SSID != "<unknown ssid>" }
                    .distinctBy { it.SSID }
                    .map { Ap(it.SSID, it.level, security(it.capabilities)) }
                    .sortedByDescending { it.level }.toList()
            } catch (_: Exception) { emptyList() }
            done(list)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) deliver()
            }
        }
        try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else app.registerReceiver(receiver, filter)
            val accepted = w.startScan()
            // Android can throttle scans. A broadcast is preferred, but the cache is
            // still returned after the timeout if the platform declines the request.
            handler.postDelayed({ deliver() }, if (accepted) 8_000L else 1_000L)
        } catch (_: Exception) {
            deliver()
        }
    }

    private fun locationEnabled(c: Context): Boolean = try {
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (_: Exception) { false }

    private fun security(caps: String): String = when {
        caps.contains("WPA3") -> "WPA3"
        caps.contains("WPA2") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        else -> "open"
    }

    @Suppress("DEPRECATION")
    fun join(c: Context, ssid: String, pass: String): String {
        val w = wifi(c)
        if (!w.isWifiEnabled) return "Wi-Fi is off; enable it in Android settings"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
                if (pass.isNotEmpty()) b.setWpa2Passphrase(pass)
                val result = w.addNetworkSuggestions(listOf(b.build()))
                if (result == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
                    "Android accepted the suggestion for $ssid. Approve the system prompt if shown."
                else "Android rejected the suggestion ($result)"
            } else {
                val cfg = android.net.wifi.WifiConfiguration().apply {
                    SSID = "\"" + ssid + "\""
                    if (pass.isNotEmpty()) preSharedKey = "\"" + pass + "\""
                }
                val id = w.addNetwork(cfg)
                if (id < 0) "Could not add $ssid"
                else {
                    w.disconnect(); w.enableNetwork(id, true); w.reconnect()
                    "Join requested for $ssid"
                }
            }
        } catch (e: Exception) { "Join failed: ${e.message}" }
    }

    fun openSettings(c: Context) {
        c.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openMobileSettings(c: Context) {
        val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        try { c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) {
            c.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}