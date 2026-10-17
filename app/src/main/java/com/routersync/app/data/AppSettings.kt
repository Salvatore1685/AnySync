package com.routersync.app.data

import android.content.Context

/** Impostazioni generali dell'app, salvate localmente sul dispositivo (non sincronizzate). */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    /** Se false, non viene mai inviata alcuna notifica di spazio HDD in esaurimento. */
    var lowSpaceNotificationsEnabled: Boolean
        get() = prefs.getBoolean("low_space_notif_enabled", true)
        set(value) = prefs.edit().putBoolean("low_space_notif_enabled", value).apply()

    /** Timestamp (epoch millis) fino al quale le notifiche di spazio sono posticipate temporaneamente. */
    var lowSpaceNotificationSnoozeUntil: Long
        get() = prefs.getLong("low_space_notif_snooze_until", 0L)
        set(value) = prefs.edit().putLong("low_space_notif_snooze_until", value).apply()

    /** True se in questo momento le notifiche di spazio vanno effettivamente inviate. */
    fun shouldNotifyLowSpace(): Boolean =
        lowSpaceNotificationsEnabled && System.currentTimeMillis() > lowSpaceNotificationSnoozeUntil

    /** Avvisa quando una sync pianificata non va a buon fine da troppo tempo. */
    var staleSyncNotificationsEnabled: Boolean
        get() = prefs.getBoolean("stale_sync_notif_enabled", true)
        set(value) = prefs.edit().putBoolean("stale_sync_notif_enabled", value).apply()

    /** Ultimo momento in cui è stato inviato l'avviso "sync ferma" per questo profilo (per non ripeterlo di continuo). */
    fun lastStaleNotification(profileId: Long): Long = prefs.getLong("stale_notified_$profileId", 0L)
    fun setLastStaleNotification(profileId: Long, value: Long) =
        prefs.edit().putLong("stale_notified_$profileId", value).apply()

    /** Backup automatico settimanale delle sync in una cartella scelta dall'utente. */
    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup_enabled", false)
        set(value) = prefs.edit().putBoolean("auto_backup_enabled", value).apply()

    var autoBackupFolderUri: String?
        get() = prefs.getString("auto_backup_folder_uri", null)
        set(value) = prefs.edit().putString("auto_backup_folder_uri", value).apply()

    var lastAutoBackupTimestamp: Long
        get() = prefs.getLong("auto_backup_last", 0L)
        set(value) = prefs.edit().putLong("auto_backup_last", value).apply()
}
