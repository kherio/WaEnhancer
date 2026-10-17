package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.SharedPreferences
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.toolkit.backup.EncryptedBackup
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scheduled, encrypted local backups of the chat databases (and, optionally, the media) that do
 * not depend on Google Drive. WhatsApp has no scheduler we can hook, so the schedule is checked
 * whenever WhatsApp comes to the foreground: if the chosen interval has passed, a backup runs.
 */
class AutoBackup(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    private val running = AtomicBoolean(false)

    override fun getPluginName(): String = "Auto Backup"

    override fun doHook() {
        if (!xprefs.getBoolean("auto_backup", false)) return

        WppCore.addListenerActivity(object : WppCore.ActivityChangeState {
            override fun onChange(activity: Activity, type: WppCore.ActivityChangeState.ChangeType) {
                if (type == WppCore.ActivityChangeState.ChangeType.RESUMED) maybeRun()
            }
        })
    }

    private fun maybeRun() {
        val password = xprefs.getString("auto_backup_password", "").orEmpty()
        if (password.isBlank()) return
        val intervalHours = xprefs.getString("auto_backup_interval", "24")?.toLongOrNull() ?: 24L
        val keep = xprefs.getString("auto_backup_keep", "3")?.toIntOrNull() ?: 3
        val includeMedia = xprefs.getBoolean("auto_backup_media", false)

        val now = System.currentTimeMillis()
        val last = WppCore.getPrivString(LAST_KEY, "0")?.toLongOrNull() ?: 0L
        if (now - last < intervalHours * HOUR_MS) return
        if (!running.compareAndSet(false, true)) return

        Thread {
            try {
                val backup = EncryptedBackup(Utils.application)
                val chars = password.toCharArray()
                val db = backup.createDatabaseBackup(chars, keep)
                var total = db.sizeBytes
                if (includeMedia) {
                    val since = WppCore.getPrivString(MEDIA_KEY, "0")?.toLongOrNull() ?: 0L
                    backup.createMediaBackup(chars, since)?.let { total += it.sizeBytes }
                    WppCore.setPrivString(MEDIA_KEY, now.toString())
                }
                WppCore.setPrivString(LAST_KEY, now.toString())
                toast(R.string.auto_backup_done, "%.1f MB".format(total / 1048576.0))
            } catch (e: Throwable) {
                logDebug(e)
                toast(R.string.auto_backup_failed, e.message ?: e.javaClass.simpleName)
            } finally {
                running.set(false)
            }
        }.apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
            start()
        }
    }

    private fun toast(res: Int, arg: String) {
        val context = WppCore.getCurrentActivity() ?: Utils.application
        Utils.showToast(context.getString(res, arg), Toast.LENGTH_LONG)
    }

    private companion object {
        private const val HOUR_MS = 3_600_000L
        private const val LAST_KEY = "auto_backup_last"
        private const val MEDIA_KEY = "auto_backup_media_last"
    }
}
