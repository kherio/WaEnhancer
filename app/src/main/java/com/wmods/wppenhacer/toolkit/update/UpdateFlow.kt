package com.wmods.wppenhacer.toolkit.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.toolkit.Dlg
import com.wmods.wppenhacer.toolkit.Host
import com.wmods.wppenhacer.toolkit.S
import java.io.File

/**
 * "Buscar actualizaciones": consulta la última release en GitHub, descarga el APK desde el propio
 * módulo y lo instala: directamente con root, o abriendo el instalador del sistema si no hay root.
 */
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
            try {
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, rel.apkName)
                Updater.downloadTo(rel.apkUrl, file)
                val res = Updater.installWithRoot(file)
                if (res == null) {
                    // Sin root: se abre el instalador del sistema con el APK ya descargado.
                    activity.runOnUiThread { installWithSystemInstaller(file) }
                    return@Thread
                }
                val installed = if (res.first) Updater.installedVersionWithRoot(BuildConfig.APPLICATION_ID) else null
                file.delete()
                activity.runOnUiThread {
                    if (res.first && (installed == null || installed == rel.version)) {
                        Dlg(activity).setTitle(S.get(S.update_ok_title))
                            .setMessage(S.get(S.update_ok_msg, installed ?: rel.version))
                            .setPositiveButton(S.get(S.update_restart)) { _, _ -> Host.restart(activity) }
                            .setNegativeButton(activity.getString(android.R.string.cancel), null).show()
                    } else {
                        Dlg(activity).setTitle(S.get(S.update_fail_title))
                            .setMessage(S.get(S.update_fail_msg, if (res.first) "$installed ≠ ${rel.version}" else res.second.take(300)))
                            .setPositiveButton(S.get(S.deleted_close), null).show()
                    }
                }
            } catch (e: Throwable) {
                Host.log(e)
                activity.runOnUiThread {
                    Host.toast(activity, S.get(S.update_error, e.message ?: e.javaClass.simpleName))
                }
            }
        }.start()
    }

    /** Sin root: pide el permiso de instalar apps desconocidas si falta y abre el instalador del sistema. */
    private fun installWithSystemInstaller(file: File) {
        try {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                Dlg(activity).setTitle(S.get(S.update_menu))
                    .setMessage(activity.getString(R.string.update_allow_install))
                    .setPositiveButton(activity.getString(android.R.string.ok)) { _, _ ->
                        activity.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                        )
                    }
                    .setNegativeButton(activity.getString(android.R.string.cancel), null)
                    .show()
                return
            }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        } catch (e: Throwable) {
            Host.log(e)
            Host.toast(activity, S.get(S.update_error, e.message ?: e.javaClass.simpleName))
        }
    }
}
