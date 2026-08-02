package com.mj.yata

import com.mj.yata.util.BackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val payload = """{"version":4,"tasks":[{"id":1,"title":"buy milk"}]}""".toByteArray()

    @Test
    fun roundTripsThePayload() {
        val blob = BackupCrypto.encrypt(payload, "correct horse battery staple")
        assertArrayEquals(payload, BackupCrypto.decrypt(blob, "correct horse battery staple"))
    }

    @Test
    fun ciphertextDoesNotLeakThePlaintext() {
        val blob = BackupCrypto.encrypt(payload, "pw")
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("buy milk"))
    }

    @Test
    fun encryptedBlobsAreRecognisedAndPlainOnesAreNot() {
        assertTrue(BackupCrypto.isEncrypted(BackupCrypto.encrypt(payload, "pw")))
        // Restores of pre-encryption backups have to keep working.
        assertFalse(BackupCrypto.isEncrypted(payload))
    }

    @Test
    fun sameInputEncryptsDifferentlyEachTime() {
        // Random salt+IV per backup: identical data must not produce identical files.
        val first = BackupCrypto.encrypt(payload, "pw")
        val second = BackupCrypto.encrypt(payload, "pw")
        assertFalse(first.contentEquals(second))
    }

    @Test(expected = Exception::class)
    fun wrongPassphraseFails() {
        BackupCrypto.decrypt(BackupCrypto.encrypt(payload, "right"), "wrong")
    }

    @Test(expected = Exception::class)
    fun tamperedPayloadFails() {
        // GCM authenticates, so a truncated upload must refuse to decrypt rather than restore a
        // partial database.
        val blob = BackupCrypto.encrypt(payload, "pw")
        BackupCrypto.decrypt(blob.copyOfRange(0, blob.size - 4), "pw")
    }
}
