package com.routersync.app.data

import android.content.Context

/** Impostazioni generali dell'app, salvate localmente sul dispositivo (non sincronizzate). */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    /** Soglia (in GB) sotto la quale mostrare un avviso di spazio HDD in esaurimento. */
    var storageWarningThresholdGb: Int
        get() = prefs.getInt("storage_warning_gb", 5)
        set(value) = prefs.edit().putInt("storage_warning_gb", value).apply()
}
