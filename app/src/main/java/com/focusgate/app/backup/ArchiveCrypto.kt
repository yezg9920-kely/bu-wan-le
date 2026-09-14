package com.focusgate.app.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ArchiveCrypto {
    private val magic = byteArrayOf('B'.code.toByte(), 'W'.code.toByte(), 'L'.code.toByte(), '2'.code.toByte())
    private const val saltLength = 16
    private const val ivLength = 12
    private const val iterations = 120_000

    fun encrypt(plainText: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.size >= 8) { "口令至少需要8个字符" }
        val salt = ByteArray(saltLength).also(SecureRandom()::nextBytes)
        val iv = ByteArray(ivLength).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(magic)
        val encrypted = cipher.doFinal(plainText)
        return ByteBuffer.allocate(magic.size + salt.size + iv.size + encrypted.size)
            .put(magic)
            .put(salt)
            .put(iv)
            .put(encrypted)
            .array()
    }

    fun decrypt(archive: ByteArray, passphrase: CharArray): ByteArray {
        require(archive.size > magic.size + saltLength + ivLength) { "备份文件不完整" }
        val buffer = ByteBuffer.wrap(archive)
        val actualMagic = ByteArray(magic.size).also(buffer::get)
        require(actualMagic.contentEquals(magic)) { "不是受支持的不玩了备份文件" }
        val salt = ByteArray(saltLength).also(buffer::get)
        val iv = ByteArray(ivLength).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
                updateAAD(magic)
                doFinal(encrypted)
            }
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("口令错误或备份文件已损坏")
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
