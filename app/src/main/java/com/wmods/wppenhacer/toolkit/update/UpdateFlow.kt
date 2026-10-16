package com.wmods.wppenhacer.toolkit.update

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.S

/** "Buscar actualizaciones": consulta GitHub y descarga el APK nuevo; se instala tocando la notificación. */
class UpdateFlow(private val activity: Activity) {

    private val isBusiness = BuildConfig.APPLICATION_ID.endsWith(".w4b")

    fun start() {
        Host.toast(activity, S.get(S.update_checking))
        Thread {
            try {
                val rel = Updater.fetchLatest(isBusiness)
                activity.runOnUiThread {
                    if (rel == null || !Updater.isNewer(rel.version, BuildConfig.VERSION_NAME)) {
                        Host.toast(activity, S.get(S.update_none, BuildConfig.VERSION_NAME))
                    } else offer(rel)
                }
            } catch (e: Throwable) {
                Host.log(e)
                activity.runOnUiThread {
                    Host.toast(activity, S.get(S.update_error, e.message ?: e.javaClass.simpleName))
                }
            }
        }.start()
    }

    private fun offer(rel: Updater.Release) {
        Dlg(activity).setTitle(S.get(S.update_menu))
            .setMessage(S.get(S.update_available, rel.version, BuildConfig.VERSION_NAME))
            .setPositiveButton(S.get(S.update_download)) { _, _ -> download(rel) }
            .setNegativeButton(activity.getString(android.R.string.cancel), null)
            .show()
    }

    private fun download(rel: Updater.Release) {
        Host.toast(activity, S.get(S.update_downloading))
        Thread {
            var file: java.io.File? = null
            try {
                file = java.io.File(activity.cacheDir, rel.apkName)
                Updater.downloadTo(rel.apkUrl, file)
                val res = Updater.installWithRoot(file)
                val installed = if (res?.first == true) Updater.installedVersionWithRoot(BuildConfig.APPLICATION_ID) else null
                activity.runOnUiThread {
                    when {
                        res == null -> manualDownload(rel)
                        res.first && (installed == null || installed == rel.version) ->
                            Dlg(activity).setTitle(S.get(S.update_ok_title))
                                .setMessage(S.get(S.update_ok_msg, installed ?: rel.version))
                                .setPositiveButton(S.get(S.update_restart)) { _, _ -> Host.restart(activity) }
                                .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
                        else -> Dlg(activity).setTitle(S.get(S.update_fail_title))
                            .setMessage(S.get(S.update_fail_msg, if (res.first) "$installed ≠ ${rel.version}" else res.second.take(300)))
                            .setPositiveButton(S.get(S.deleted_close), null).show()
                    }
                }
            } catch (e: Throwable) {
                Host.log(e)
                activity.runOnUiThread {
                    Host.toast(activity, S.get(S.update_error, e.message ?: e.javaClass.simpleName))
                }
            } finally {
                file?.delete()
            }
        }.start()
    }

    /** Sin root: se descarga con el gestor de descargas y se instala tocando la notificación. */
    private fun manualDownload(rel: Updater.Release) {
        try {
            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(rel.apkUrl))
                .setTitle(rel.apkName)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, rel.apkName)
            dm.enqueue(req)
            Host.toast(activity, S.get(S.update_started), android.widget.Toast.LENGTH_LONG)
        } catch (e: Throwable) {
            Host.log(e)
            Host.toast(activity, S.get(S.update_error, e.message ?: e.javaClass.simpleName))
        }
    }
}
