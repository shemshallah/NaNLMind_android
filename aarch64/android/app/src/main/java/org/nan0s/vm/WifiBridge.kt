package org.nan0s.vm

import android.Manifest
import android.app.Activity
import android.content.Context
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
        val n = cm.activeNetwork
        val caps = if (n != null) cm.getNetworkCapabilities(n) else null
        if (caps == null) return "NaNNet: no active Android network"
        val sb = StringBuilder()
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val info = w.connectionInfo
                val ssid = info.ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: "SSID hidden"
                sb.append("WI-FI • ").append(if (w.isWifiEnabled) "ON" else "OFF")
                    .append("\nSSID: ").append(ssid)
                    .append("\nSignal: ").append(info.rssi).append(" dBm")
                if (info.linkSpeed > 0) sb.append(" • ").append(info.linkSpeed).append(" Mbps link")
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                sb.append("CELLULAR • connected\nMobile data is the active route")
                if (Build.VERSION.SDK_INT >= 29) sb.append("\nDownstream: ").append(caps.linkDownstreamBandwidthKbps).append(" kbps")
                sb.append("\n5G/LTE selection is controlled by Android and the carrier")
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> sb.append("ETHERNET • connected")
            else -> sb.append("ANDROID NETWORK • connected")
        }
        sb.append("\nInternet: ").append(if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) "available" else "local only")
        sb.append("\nValidated: ").append(if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "yes" else "not verified")
        sb.append("\nNaNNet route: Android default network")
        return sb.toString()
    }

    data class Ap(val ssid: String, val level: Int, val security: String)

    @Suppress("DEPRECATION")
    fun scan(c: Context, done: (List<Ap>) -> Unit) {
        val w = wifi(c)
        if (!w.isWifiEnabled || !locationEnabled(c)) { done(emptyList()); return }
        try { w.startScan() } catch (_: Exception) { }
        // Android throttles scans; use the refreshed cache instead of treating a delayed scan as missing hardware.
        Handler(Looper.getMainLooper()).postDelayed({
            val list = try {
                w.scanResults.asSequence().filter { it.SSID.isNotBlank() }
                    .distinctBy { it.SSID }
                    .map { Ap(it.SSID, it.level, security(it.capabilities)) }
                    .sortedByDescending { it.level }.toList()
            } catch (_: Exception) { emptyList() }
            done(list)
        }, 2200)
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
        c.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}