package com.wmods.wppenhacer.xposed.features.privacy

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.media.ExifInterface
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.content.SharedPreferences
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.utils.Utils
import java.io.File
import java.util.UUID

/**
 * Removes from images (JPEG, PNG, WebP) the metadata that reveals personal data (GPS location,
 * phone model, dates, author, comments) when WhatsApp reads them from another app (gallery,
 * document picker, share). Orientation is kept so the image does not come out rotated.
 */
class MetadataStrip(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    override fun getPluginName(): String = "Metadata Strip"

    private val busy = ThreadLocal<Boolean>()

    override fun doHook() {
        if (!xprefs.getBoolean("strip_metadata", false)) return

        ContentResolver::class.java.resolve().firstMethod {
            name = "openAssetFileDescriptor"
            parameters(Uri::class.java, String::class.java, CancellationSignal::class.java)
        }.hook {
            after {
                if (busy.get() == true) return@after
                val original = result as? AssetFileDescriptor ?: return@after
                val uri = args[0] as? Uri ?: return@after
                if (args[1] != "r" || uri.scheme != ContentResolver.SCHEME_CONTENT) return@after
                val authority = uri.authority ?: return@after
                if (authority.startsWith("com.whatsapp") || authority.startsWith("com.wmods")) return@after
                busy.set(true)
                try {
                    val resolver = instance as ContentResolver
                    val ext = when (resolver.getType(uri)) {
                        "image/jpeg" -> "jpg"
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        else -> return@after
                    }
                    val stripped = strip(original, ext) ?: return@after
                    result = stripped
                } catch (e: Throwable) {
                    logDebug(e)
                } finally {
                    busy.set(false)
                }
            }
        }
    }

    /** Copies the image to a temporary file, strips its metadata and returns a read-only descriptor. */
    private fun strip(original: AssetFileDescriptor, ext: String): AssetFileDescriptor? {
        val dir = File(Utils.application.cacheDir, "stripped").apply { mkdirs() }
        val tmp = File(dir, "${UUID.randomUUID()}.$ext")
        try {
            original.createInputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            original.close()
            clearMetadata(tmp)
            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            return AssetFileDescriptor(pfd, 0, tmp.length())
        } catch (e: Throwable) {
            logDebug(e)
            return null
        } finally {
            // The already open descriptor stays valid even if the file is deleted.
            tmp.delete()
        }
    }

    private fun clearMetadata(file: File) {
        val exif = ExifInterface(file.absolutePath)
        val tags = HashSet<String>(PRIVATE_TAGS)
        for (field in ExifInterface::class.java.fields) {
            if (field.name.startsWith("TAG_GPS_") && field.type == String::class.java) {
                (field.get(null) as? String)?.let { tags.add(it) }
            }
        }
        for (tag in tags) exif.setAttribute(tag, null)
        exif.saveAttributes()
    }

    private companion object {
        private val PRIVATE_TAGS = listOf(
            "Make", "Model", "Software", "DateTime", "DateTimeOriginal", "DateTimeDigitized",
            "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
            "SubSecTime", "SubSecTimeOriginal", "SubSecTimeDigitized",
            "Artist", "Copyright", "ImageDescription", "UserComment", "MakerNote",
            "BodySerialNumber", "CameraOwnerName", "LensMake", "LensModel", "LensSerialNumber",
            "ImageUniqueID", "HostComputer", "DeviceSettingDescription"
        )
    }
}
