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

    /**
     * Descrive in modo leggibile perché la condizione di rete non è soddisfatta in questo
     * momento, per mostrare all'utente un messaggio utile invece di un generico "in attesa".
     */
    fun reasonNotSatisfied(context: Context, profile: SyncProfile): String {
        val current = currentWifiSsid(context)
        return when (profile.networkPreference) {
            NetworkPreference.ANY -> "" // non dovrebbe mai capitare: ANY è sempre soddisfatta
            NetworkPreference.WIFI_ONLY -> "in attesa di una connessione Wi-Fi (al momento non rilevata)"
            NetworkPreference.HOME_WIFI_ONLY -> when {
                profile.homeWifiSsid == null ->
                    "nessun Wi-Fi di casa impostato per questa sync — modificala e rileva il Wi-Fi di casa mentre sei connesso ad esso"
                current == null ->
                    "in attesa del Wi-Fi di casa \"${profile.homeWifiSsid}\" (al momento non sei connesso a nessun Wi-Fi)"
                else ->
                    "in attesa del Wi-Fi di casa \"${profile.homeWifiSsid}\" (sei connesso a \"$current\")"
            }
            NetworkPreference.MOBILE_ONLY -> "in attesa dei dati mobili (richiede anche un IP pubblico funzionante sulla linea di casa)"
            NetworkPreference.HOME_WIFI_OR_MOBILE -> when {
                profile.homeWifiSsid == null ->
                    "nessun Wi-Fi di casa impostato, e non sei sui dati mobili — modifica la sync per impostarlo"
                else ->
                    "in attesa del Wi-Fi di casa \"${profile.homeWifiSsid}\" o dei dati mobili (sei connesso a \"${current ?: "nessuna rete Wi-Fi"}\")"
            }
        }
    }
}
