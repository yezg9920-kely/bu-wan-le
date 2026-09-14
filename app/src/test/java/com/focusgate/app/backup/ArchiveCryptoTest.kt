package com.focusgate.app.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ArchiveCryptoTest {
    @Test
    fun `round trip keeps bytes`() {
        val plain = "不玩了迁移数据".toByteArray()
        val encrypted = ArchiveCrypto.encrypt(plain, "correct-pass".toCharArray())

        assertArrayEquals(plain, ArchiveCrypto.decrypt(encrypted, "correct-pass".toCharArray()))
    }

    @Test
    fun `wrong passphrase is rejected`() {
        val encrypted = ArchiveCrypto.encrypt("data".toByteArray(), "correct-pass".toCharArray())

        assertThrows(IllegalArgumentException::class.java) {
            ArchiveCrypto.decrypt(encrypted, "wrong-pass".toCharArray())
        }
    }

    @Test
    fun `tampering is rejected`() {
        val encrypted = ArchiveCrypto.encrypt("data".toByteArray(), "correct-pass".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last() + 1).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            ArchiveCrypto.decrypt(encrypted, "correct-pass".toCharArray())
        }
    }
}
