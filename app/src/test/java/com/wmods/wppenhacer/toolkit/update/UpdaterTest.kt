package com.wmods.wppenhacer.toolkit.update

import org.junit.Assert.*
import org.junit.Test

class UpdaterTest {
    @Test fun newer() {
        assertTrue(Updater.isNewer("v0.4.0", "0.3.0"))
        assertTrue(Updater.isNewer("0.10.0", "0.9.5"))
        assertTrue(Updater.isNewer("1.0", "0.9.9"))
    }
    @Test fun notNewer() {
        assertFalse(Updater.isNewer("v0.3.0", "0.3.0"))
        assertFalse(Updater.isNewer("0.2.9", "0.3.0"))
        assertFalse(Updater.isNewer("0.3", "0.3.0"))
    }
    @Test fun parseRelease() {
        val r = Updater.parse("""{"tag_name":"v0.4.0","assets":[{"name":"x.txt","browser_download_url":"u1"},{"name":"waimport-0.4.0.apk","browser_download_url":"u2"}]}""")!!
        assertEquals("0.4.0", r.version); assertEquals("u2", r.apkUrl); assertEquals("waimport-0.4.0.apk", r.apkName)
    }
    @Test fun parseWithoutApk() {
        assertNull(Updater.parse("""{"tag_name":"v1","assets":[]}"""))
    }
    @Test fun tagWithSha() {
        assertTrue(Updater.isNewer("1.2.0-abcd1234", "1.1.9"))
        assertFalse(Updater.isNewer("1.2.0-abcd1234", "1.2.0"))
    }
    @Test fun parsePicksFlavor() {
        val j = """{"tag_name":"1.0-aa","assets":[{"name":"WaToolkit-Business-1.0.apk","browser_download_url":"b"},{"name":"WaToolkit-1.0.apk","browser_download_url":"w"}]}"""
        assertEquals("w", Updater.parse(j, false)!!.apkUrl)
        assertEquals("b", Updater.parse(j, true)!!.apkUrl)
    }
}
