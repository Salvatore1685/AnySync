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
                val homes = homeSsidList(profile.homeWifiSsid)
                val current = currentWifiSsid(context)
                homes.isNotEmpty() && current != null && homes.contains(current)
            }
            NetworkPreference.MOBILE_ONLY -> isOnMobileData(context)
            NetworkPreference.HOME_WIFI_OR_MOBILE -> {
                val homes = homeSsidList(profile.homeWifiSsid)
                val current = currentWifiSsid(context)
                (homes.isNotEmpty() && current != null && homes.contains(current)) || isOnMobileData(context)
            }
        }
    }

    /** Le reti "di casa" sono salvate come elenco separato da "\n" in un unico campo testo. */
    fun homeSsidList(stored: String?): List<String> =
        stored?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    fun joinHomeSsidList(list: List<String>): String? =
        list.filter { it.isNotBlank() }.distinct().joinToString("\n").ifBlank { null }

    /**
     * Descrive in modo leggibile perché la condizione di rete non è soddisfatta in questo
     * momento, per mostrare all'utente un messaggio utile invece di un generico "in attesa".
     */
    fun reasonNotSatisfied(context: Context, profile: SyncProfile): String {
        val current = currentWifiSsid(context)
        val homes = homeSsidList(profile.homeWifiSsid)
        val homesLabel = homes.joinToString(" / ") { "\"$it\"" }
        return when (profile.networkPreference) {
            NetworkPreference.ANY -> "" // non dovrebbe mai capitare: ANY è sempre soddisfatta
            NetworkPreference.WIFI_ONLY -> "in attesa di una connessione Wi-Fi (al momento non rilevata)"
            NetworkPreference.HOME_WIFI_ONLY -> when {
                homes.isEmpty() ->
                    "nessuna rete Wi-Fi di casa impostata per questa sync — modificala e aggiungine almeno una"
                current == null ->
                    "in attesa di una delle reti Wi-Fi di casa ($homesLabel) — al momento non sei connesso a nessun Wi-Fi"
                else ->
                    "in attesa di una delle reti Wi-Fi di casa ($homesLabel) — sei connesso a \"$current\""
            }
            NetworkPreference.MOBILE_ONLY -> "in attesa dei dati mobili (richiede anche un IP pubblico funzionante sulla linea di casa)"
            NetworkPreference.HOME_WIFI_OR_MOBILE -> when {
                homes.isEmpty() ->
                    "nessuna rete Wi-Fi di casa impostata, e non sei sui dati mobili — modifica la sync per aggiungerne una"
                else ->
                    "in attesa di una rete Wi-Fi di casa ($homesLabel) o dei dati mobili (sei connesso a \"${current ?: "nessuna rete Wi-Fi"}\")"
            }
        }
    }

    /**
     * Cerca le reti Wi-Fi visibili nei dintorni. Android limita molto la possibilità di
     * avviare una scansione esplicita (per risparmio batteria): questa funzione la richiede
     * comunque (best-effort, il sistema può ignorarla se chiamata troppo di frequente), poi
     * legge l'elenco più recente disponibile — che il telefono tiene comunque aggiornato in
     * background mentre il Wi-Fi è acceso, quindi il risultato resta utile anche quando la
     * richiesta esplicita viene ignorata.
     */
    @Suppress("DEPRECATION")
    fun scanNearbyNetworks(context: Context): List<String> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()
        return try {
            runCatching { wifiManager.startScan() }
            wifiManager.scanResults
                .sortedByDescending { it.level } // segnale più forte prima
                .mapNotNull { it.SSID?.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        } catch (e: SecurityException) {
            emptyList()
        }
    }
}
