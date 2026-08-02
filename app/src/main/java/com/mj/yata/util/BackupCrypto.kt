package com.mj.yata.util

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based encryption for backups that leave the device.
 *
 * Deliberately *not* the [androidx.security.crypto.MasterKey] mechanism
 * [com.mj.yata.data.local.backup.LocalBackupManager] uses. That key lives in the Android Keystore
 * and never leaves the phone, which is exactly right for an on-device backup and exactly wrong for
 * an off-device one: the whole point of a remote backup is restoring it onto a *different* device,
 * and a Keystore-bound key is gone the moment the original phone is lost, reset, or replaced —
 * taking every remote backup with it. A passphrase the user knows travels with them; the Keystore
 * key cannot.
 *
 * Format: `YATAENC1` magic || 16-byte salt || 12-byte IV || AES-256-GCM ciphertext (128-bit tag).
 * Salt and IV are random per backup, so two backups of identical data never produce identical
 * bytes. GCM's tag authenticates the payload, so a truncated or tampered file fails to decrypt
 * rather than restoring partial data.
 */
object BackupCrypto {

    private const val MAGIC = "YATAENC1"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    /** Cost parameter for the KDF. High enough to make offline guessing of a weak passphrase
     * expensive, low enough to stay well under a second on the phones this app targets. */
    private const val PBKDF2_ITERATIONS = 120_000

    private val magicBytes = MAGIC.toByteArray(Charsets.US_ASCII)

    /** True when [bytes] carries this format's header — lets restore accept older plain backups. */
    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > magicBytes.size && magicBytes.indices.all { bytes[it] == magicBytes[it] }

    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plain)

        return magicBytes + salt + iv + ciphertext
    }

    /**
     * Throws when the passphrase is wrong or the file is damaged — GCM cannot tell those apart,
     * and neither can we, so callers surface a single "wrong passphrase or corrupt backup"
     * message rather than guessing which it was.
     */
    fun decrypt(blob: ByteArray, passphrase: String): ByteArray {
        require(isEncrypted(blob)) { "Not an encrypted YATA backup" }
        val saltStart = magicBytes.size
        val ivStart = saltStart + SALT_BYTES
        val bodyStart = ivStart + IV_BYTES
        require(blob.size > bodyStart) { "Encrypted backup is truncated" }

        val salt = blob.copyOfRange(saltStart, ivStart)
        val iv = blob.copyOfRange(ivStart, bodyStart)
        val ciphertext = blob.copyOfRange(bodyStart, blob.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
