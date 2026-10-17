package com.wmods.wppenhacer.toolkit.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.Random

class BackupCryptoTest {
    private fun enc(data: ByteArray, pw: String): ByteArray {
        val bo = ByteArrayOutputStream()
        BackupCrypto.EncryptingOutputStream(bo, pw.toCharArray()).use { it.write(data) }
        return bo.toByteArray()
    }
    private fun dec(data: ByteArray, pw: String): ByteArray {
        val bo = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(data), bo, pw.toCharArray())
        return bo.toByteArray()
    }
    @Test fun roundTripSizes() {
        for (n in intArrayOf(0, 1, 1000, (1 shl 20) - 1, 1 shl 20, (1 shl 20) + 1, 3 * (1 shl 20) + 5)) {
            val d = ByteArray(n).also { Random(n.toLong()).nextBytes(it) }
            assertArrayEquals("size $n", d, dec(enc(d, "pw"), "pw"))
        }
    }
    @Test fun wrongPassword() {
        val e = enc(ByteArray(100) { 7 }, "a")
        try { dec(e, "b"); fail() } catch (_: BackupCrypto.WrongPasswordException) {}
    }
    @Test fun truncationDetected() {
        val e = enc(ByteArray(3 * (1 shl 20)) { 1 }, "a")
        val cut = e.copyOf(e.size - 40)
        try { dec(cut, "a"); fail() } catch (_: IOException) {}
    }
}
