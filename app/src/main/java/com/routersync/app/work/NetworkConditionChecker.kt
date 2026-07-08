package com.routersync.app.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.routersync.app.data.NetworkPreference
import com.routersync.app.data.SyncProfile

/**
 * Verifica se le condizioni di rete richieste da un profilo sono soddisfatte in questo
 * momento. Usato dal [SyncWorker] per decidere se procedere o riprovare più tardi.
 */
object NetworkConditionChecker {

    /** Nome (SSID) della rete Wi-Fi a cui il telefono è attualmente connesso, o null se non in Wi-Fi. */
    fun currentWifiSsid(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ssid = wifiManager?.connectionInfo?.ssid ?: return null
        // L'SSID può arrivare tra virgolette (es. "\"CasaMia\"") a seconda della versione Android
        return ssid.trim('"').takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    private fun isOnMobileData(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    private fun isOnAnyWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** True se la condizione di rete scelta per il profilo è soddisfatta adesso. */
    fun isSatisfied(context: Context, profile: SyncProfile): Boolean {
        return when (profile.networkPreference) {
            NetworkPreference.ANY -> true
            NetworkPreference.WIFI_ONLY -> isOnAnyWifi(context)
            NetworkPreference.HOME_WIFI_ONLY -> {
                val home = profile.homeWifiSsid
                home != null && currentWifiSsid(context) == home
            }
            NetworkPreference.MOBILE_ONLY -> isOnMobileData(context)
            NetworkPreference.HOME_WIFI_OR_MOBILE -> {
                val home = profile.homeWifiSsid
                (home != null && currentWifiSsid(context) == home) || isOnMobileData(context)
            }
        }
    }
}
