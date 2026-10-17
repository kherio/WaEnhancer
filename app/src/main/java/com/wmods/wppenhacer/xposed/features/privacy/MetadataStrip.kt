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
 * Borra de las imágenes (JPEG, PNG, WebP) los metadatos que revelan datos personales —ubicación GPS,
 * modelo del móvil, fechas, autor, comentarios— cuando WhatsApp las lee desde otra app (galería,
 * selector de documentos, compartir). La orientación se conserva para que la imagen no salga girada.
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

    /** Copia la imagen a un archivo temporal, le borra los metadatos y devuelve un descriptor de solo lectura. */
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
            // El descriptor ya abierto sigue siendo válido aunque se elimine el archivo.
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
