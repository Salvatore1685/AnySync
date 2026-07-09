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
}
