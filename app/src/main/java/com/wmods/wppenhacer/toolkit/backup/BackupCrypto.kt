package com.wmods.wppenhacer.toolkit.backup

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming password-based encryption for WaToolkit backups.
 *
 * Format: magic "WTBK" + version, 16-byte salt, PBKDF2 iteration count, 8-byte IV prefix, then a
 * sequence of AES-256-GCM chunks (each: int length, "last chunk" flag, ciphertext + tag). The chunk
 * index and the last-chunk flag are authenticated, so reordering or truncating a backup is detected.
 */
object BackupCrypto {

    class WrongPasswordException : IOException("Wrong password or corrupted backup")

    private val MAGIC = byteArrayOf('W'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), 1)
    private const val CHUNK = 1 shl 20
    private const val ITERATIONS = 210_000
    private const val SALT_SIZE = 16
    private const val IV_PREFIX_SIZE = 8

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, ivPrefix: ByteArray, index: Long, last: Boolean): Cipher {
        val iv = ByteBuffer.allocate(12).put(ivPrefix).putInt(index.toInt()).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(ByteBuffer.allocate(9).putLong(index).put(if (last) 1 else 0).array())
        return cipher
    }

    /** Encrypts everything written to it into [out]. [close] writes the final chunk and closes [out]. */
    class EncryptingOutputStream(private val out: OutputStream, password: CharArray) : OutputStream() {
        private val ivPrefix = ByteArray(IV_PREFIX_SIZE).also { SecureRandom().nextBytes(it) }
        private val key: SecretKeySpec
        private val buffer = ByteArray(CHUNK)
        private var filled = 0
        private var index = 0L
        private var closed = false

        init {
            val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
            key = deriveKey(password, salt, ITERATIONS)
            out.write(MAGIC)
            out.write(salt)
            out.write(ByteBuffer.allocate(4).putInt(ITERATIONS).array())
            out.write(ivPrefix)
        }

        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val n = minOf(remaining, CHUNK - filled)
                System.arraycopy(b, offset, buffer, filled, n)
                filled += n
                offset += n
                remaining -= n
                if (filled == CHUNK) writeChunk(last = false)
            }
        }

        private fun writeChunk(last: Boolean) {
            val encrypted = cipher(Cipher.ENCRYPT_MODE, key, ivPrefix, index, last).doFinal(buffer, 0, filled)
            out.write(ByteBuffer.allocate(4).putInt(encrypted.size).array())
            out.write(if (last) 1 else 0)
            out.write(encrypted)
            index++
            filled = 0
        }

        override fun flush() {
            out.flush()
        }

        override fun close() {
            if (closed) return
            closed = true
            writeChunk(last = true)
            out.flush()
            out.close()
        }
    }

    /** Decrypts a backup read from [input] into [out]. Throws [WrongPasswordException] on a bad password. */
    fun decrypt(input: InputStream, out: OutputStream, password: CharArray) {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        try {
            data.readFully(magic)
        } catch (_: EOFException) {
            throw IOException("Not a WaToolkit backup")
        }
        if (!magic.contentEquals(MAGIC)) throw IOException("Not a WaToolkit backup")
        val salt = ByteArray(SALT_SIZE).also { data.readFully(it) }
        val iterations = data.readInt()
        if (iterations < 1 || iterations > 10_000_000) throw IOException("Corrupted backup header")
        val ivPrefix = ByteArray(IV_PREFIX_SIZE).also { data.readFully(it) }
        val key = deriveKey(password, salt, iterations)

        var index = 0L
        while (true) {
            val length = data.readInt()
            if (length < 16 || length > CHUNK + 16) throw IOException("Corrupted backup")
            val last = data.readUnsignedByte() == 1
            val encrypted = ByteArray(length)
            try {
                data.readFully(encrypted)
            } catch (_: EOFException) {
                throw IOException("Backup is truncated")
            }
            val plain = try {
                cipher(Cipher.DECRYPT_MODE, key, ivPrefix, index, last).doFinal(encrypted)
            } catch (_: AEADBadTagException) {
                throw WrongPasswordException()
            }
            out.write(plain)
            index++
            if (last) break
        }
    }
}
