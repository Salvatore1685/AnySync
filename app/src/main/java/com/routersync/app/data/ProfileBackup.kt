package com.routersync.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converte i profili di sync da/verso JSON, per la funzione "Esporta/Importa configurazione"
 * nelle Impostazioni. Vengono salvati solo i campi di configurazione scelti dall'utente: id,
 * stato dell'ultima sync e id del WorkManager sono esclusi perché specifici del dispositivo e
 * privi di senso se importati altrove (verranno ricreati al primo utilizzo del profilo).
 *
 * ATTENZIONE: il file esportato contiene le password delle sync in chiaro (stesso modo in cui
 * sono già salvate nel database dell'app). Va trattato come un file sensibile.
 */
object ProfileBackup {

    fun export(profiles: List<SyncProfile>): String {
        val array = JSONArray()
        profiles.forEach { array.put(toJson(it)) }
        return JSONObject().put("anysync_backup_version", 1).put("profiles", array).toString(2)
    }

    /** Ritorna la lista dei profili importati (con id=0, pronti per essere salvati come nuovi). Ignora silenziosamente eventuali voci malformate. */
    fun import(json: String): List<SyncProfile> {
        val root = JSONObject(json)
        val array = root.optJSONArray("profiles") ?: JSONArray(json) // tollera anche un semplice array, senza wrapper
        val result = mutableListOf<SyncProfile>()
        for (i in 0 until array.length()) {
            runCatching { fromJson(array.getJSONObject(i)) }.getOrNull()?.let { result += it }
        }
        return result
    }

    private fun toJson(p: SyncProfile): JSONObject = JSONObject()
        .put("name", p.name)
        .put("protocol", p.protocol.name)
        .put("host", p.host)
        .put("port", p.port)
        .put("username", p.username)
        .put("password", p.password)
        .put("remoteBasePath", p.remoteBasePath)
        .put("localFolderUri", p.localFolderUri)
        .put("localFolderDisplayName", p.localFolderDisplayName)
        .put("scheduleType", p.scheduleType.name)
        .put("direction", p.direction.name)
        .put("scheduledHour", p.scheduledHour)
        .put("scheduledMinute", p.scheduledMinute)
        .put("scheduledDayOfWeek", p.scheduledDayOfWeek)
        .put("scheduledDayOfMonth", p.scheduledDayOfMonth)
        .put("networkPreference", p.networkPreference.name)
        .put("requiresCharging", p.requiresCharging)
        .put("homeWifiSsid", p.homeWifiSsid ?: JSONObject.NULL)
        .put("autoFreeSpaceAfterSync", p.autoFreeSpaceAfterSync)
        .put("mirrorDeletes", p.mirrorDeletes)
        .put("excludedPaths", p.excludedPaths ?: JSONObject.NULL)
        .put("storageWarningThresholdGb", p.storageWarningThresholdGb)

    private fun fromJson(o: JSONObject): SyncProfile = SyncProfile(
        id = 0,
        name = o.getString("name"),
        protocol = RemoteProtocol.valueOf(o.getString("protocol")),
        host = o.getString("host"),
        port = o.getInt("port"),
        username = o.getString("username"),
        password = o.getString("password"),
        remoteBasePath = o.getString("remoteBasePath"),
        localFolderUri = o.getString("localFolderUri"),
        localFolderDisplayName = o.getString("localFolderDisplayName"),
        scheduleType = ScheduleType.valueOf(o.getString("scheduleType")),
        direction = SyncDirection.valueOf(o.getString("direction")),
        scheduledHour = o.optInt("scheduledHour", 2),
        scheduledMinute = o.optInt("scheduledMinute", 0),
        scheduledDayOfWeek = o.optInt("scheduledDayOfWeek", java.util.Calendar.MONDAY),
        scheduledDayOfMonth = o.optInt("scheduledDayOfMonth", 1),
        networkPreference = runCatching { NetworkPreference.valueOf(o.getString("networkPreference")) }.getOrDefault(NetworkPreference.ANY),
        requiresCharging = o.optBoolean("requiresCharging", false),
        homeWifiSsid = if (!o.has("homeWifiSsid") || o.isNull("homeWifiSsid")) null else o.getString("homeWifiSsid"),
        autoFreeSpaceAfterSync = o.optBoolean("autoFreeSpaceAfterSync", false),
        mirrorDeletes = o.optBoolean("mirrorDeletes", false),
        excludedPaths = if (!o.has("excludedPaths") || o.isNull("excludedPaths")) null else o.getString("excludedPaths"),
        storageWarningThresholdGb = o.optInt("storageWarningThresholdGb", 5)
    )
}
