package com.wmods.wppenhacer.toolkit

import android.content.Context

/**
 * Puente entre la app del módulo y WhatsApp: la app del módulo anota qué herramienta quiere
 * abrir y lanza WhatsApp; la función Toolkit, ya dentro de WhatsApp, recoge la petición
 * (a través de PrefsProvider) y abre la herramienta. Las peticiones caducan a los 2 minutos.
 */
object ToolRequest {
    const val IMPORT = "import"
    const val EXPORT = "export"
    const val DELETED = "deleted"
    const val RECOVER = "recover"
    const val BACKUP = "backup"

    private const val FILE = "toolkit_requests"
    private const val KEY_TOOL = "tool"
    private const val KEY_PKG = "pkg"
    private const val KEY_TIME = "time"
    private const val MAX_AGE_MS = 120_000L
    private val KNOWN = setOf(IMPORT, EXPORT, DELETED, RECOVER, BACKUP)

    /** Guarda la petición para [pkg] (WhatsApp o Business) y lanza esa app. */
    fun request(context: Context, tool: String, pkg: String): Boolean {
        if (tool !in KNOWN) return false
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOOL, tool)
            .putString(KEY_PKG, pkg)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    /** Devuelve y borra la petición pendiente para [pkg]; null si no hay o ha caducado. */
    fun take(context: Context, pkg: String): String? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val tool = prefs.getString(KEY_TOOL, null) ?: return null
        val target = prefs.getString(KEY_PKG, null)
        val age = System.currentTimeMillis() - prefs.getLong(KEY_TIME, 0L)
        if (target != pkg) {
            if (age > MAX_AGE_MS) prefs.edit().clear().apply()
            return null
        }
        prefs.edit().clear().apply()
        return if (age in 0..MAX_AGE_MS && tool in KNOWN) tool else null
    }
}
