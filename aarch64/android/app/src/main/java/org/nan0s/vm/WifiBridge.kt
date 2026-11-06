package org.nan0s.vm

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Real Android WiFi surface: link status, scan results and a connect request.
 * Uses WifiManager directly; no third-party or androidx dependency.
 * Joining on Android 10+ is a network suggestion (the system asks the user to
 * approve), which is the only supported path for a normal app.
 */
object WifiBridge {
    private fun wifi(c: Context): WifiManager =
        c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun hasPermission(c: Context): Boolean {
        val scan = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
        return c.checkSelfPermission(scan) == PackageManager.PERMISSION_GRANTED &&
            c.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(a: Activity, code: Int) {
        val ask = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        if (Build.VERSION.SDK_INT >= 33) ask.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        else ask.add(Manifest.permission.ACCESS_FINE_LOCATION)
        a.requestPermissions(ask.toTypedArray(), code)
    }

    /** One-line live status for the dialog. */
    @Suppress("DEPRECATION")
    fun status(c: Context): String {
        val w = wifi(c)
        val sb = StringBuilder(if (w.isWifiEnabled) "WiFi on" else "WiFi off")
        try {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork
            val caps = if (n != null) cm.getNetworkCapabilities(n) else null
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val info = w.connectionInfo
                sb.append(" | ").append(info.ssid).append(" ").append(info.rssi).append(" dBm")
                if (info.linkSpeed > 0) sb.append(" ").append(info.linkSpeed).append(" Mbps")
            } else if (caps != null) {
                sb.append(" | non-WiFi network")
            } else {
                sb.append(" | no network")
            }
        } catch (e: Exception) {
            sb.append(" | status unavailable")
        }
        return sb.toString()
    }

    data class Ap(val ssid: String, val level: Int, val security: String)

    /** Scan and return the visible APs after the system has refreshed results. */
    @Suppress("DEPRECATION")
    fun scan(c: Context, done: (List<Ap>) -> Unit) {
        val w = wifi(c)
        if (!w.isWifiEnabled) { done(emptyList()); return }
        try { w.startScan() } catch (e: Exception) { }
        Handler(Looper.getMainLooper()).postDelayed({
            val list = try {
                w.scanResults
                    .filter { it.SSID.isNotEmpty() }
                    .map { Ap(it.SSID, it.level, security(it.capabilities)) }
                    .sortedByDescending { it.level }
            } catch (e: Exception) { emptyList() }
            done(list)
        }, 1600)
    }

    private fun security(caps: String): String = when {
        caps.contains("WPA3") -> "WPA3"
        caps.contains("WPA2") -> "WPA2"
        caps.contains("WPA") -> "WPA"
        caps.contains("WEP") -> "WEP"
        else -> "open"
    }

    /** Add a network suggestion (API 29+) or a legacy configuration. */
    @Suppress("DEPRECATION")
    fun join(c: Context, ssid: String, pass: String): String {
        val w = wifi(c)
        if (!w.isWifiEnabled) return "WiFi is off; enable it in system settings"
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
                if (pass.isNotEmpty()) b.setWpa2Passphrase(pass)
                val r = w.addNetworkSuggestions(listOf(b.build()))
                if (r == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS)
                    "Suggestion added for $ssid; approve it in the WiFi notification"
                else "Suggestion rejected ($r)"
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
        } catch (e: Exception) {
            "Join failed: ${e.message}"
        }
    }

    fun openSettings(c: Context) {
        c.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
