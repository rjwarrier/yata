package com.mj.yata

import com.mj.yata.util.decodeSalt
import com.mj.yata.util.encodeSalt
import com.mj.yata.util.generateSalt
import com.mj.yata.util.hashPin
import com.mj.yata.util.needsRehash
import com.mj.yata.util.verifyPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class PinCodeUtilsTest {

    @Test
    fun correctPinVerifies() {
        val salt = generateSalt()
        val hash = hashPin("4821", salt)
        assertTrue(verifyPin("4821", hash, salt))
    }

    @Test
    fun wrongPinDoesNotVerify() {
        val salt = generateSalt()
        val hash = hashPin("4821", salt)
        assertFalse(verifyPin("4822", hash, salt))
        assertFalse(verifyPin("482", hash, salt))
        assertFalse(verifyPin("48210", hash, salt))
        assertFalse(verifyPin("", hash, salt))
    }

    @Test
    fun samePinUnderDifferentSaltsGivesDifferentHashes() {
        // The salt is what stops one precomputed table covering every install.
        val hashA = hashPin("1234", generateSalt())
        val hashB = hashPin("1234", generateSalt())
        assertNotEquals(hashA, hashB)
    }

    @Test
    fun legacyHashStillVerifies() {
        // A PIN set before the move to PBKDF2. Failing this would lock existing users out of
        // their own app on update, which is the one outcome this whole scheme must not produce.
        val salt = generateSalt()
        val legacyHash = legacySha256(("9137"), salt)
        assertTrue(verifyPin("9137", legacyHash, salt))
        assertFalse(verifyPin("9138", legacyHash, salt))
    }

    @Test
    fun legacyHashIsFlaggedForUpgradeAndCurrentIsNot() {
        val salt = generateSalt()
        assertTrue(needsRehash(legacySha256("5555", salt)))
        assertFalse(needsRehash(hashPin("5555", salt)))
    }

    @Test
    fun legacyPbkdf2Sha1HashStillVerifiesAndIsFlaggedForUpgrade() {
        // A PIN set after the first PBKDF2 move (SHA-1) but before the later move to SHA-256.
        // Same non-negotiable as the plain-SHA-256 legacy case: this must never lock anyone out.
        val salt = generateSalt()
        val legacyHash = legacyPbkdf2Sha1("6431", salt)
        assertTrue(verifyPin("6431", legacyHash, salt))
        assertFalse(verifyPin("6432", legacyHash, salt))
        assertTrue(needsRehash(legacyHash))
    }

    @Test
    fun saltSurvivesEncodingRoundTrip() {
        val salt = generateSalt()
        assertTrue(salt.contentEquals(decodeSalt(encodeSalt(salt))))
    }

    @Test
    fun rehashedPinStillVerifies() {
        // The upgrade path end to end: verify against the old scheme, re-hash, verify again.
        val oldSalt = generateSalt()
        val oldHash = legacySha256("7788", oldSalt)
        assertTrue(verifyPin("7788", oldHash, oldSalt))

        val newSalt = generateSalt()
        val newHash = hashPin("7788", newSalt)
        assertTrue(verifyPin("7788", newHash, newSalt))
        assertFalse(needsRehash(newHash))
    }

    @Test
    fun currentHashIsNotBarePlaintextOrSha256() {
        // Guards against the scheme silently regressing to something cheap to brute-force.
        val salt = generateSalt()
        val hash = hashPin("0000", salt)
        assertTrue(hash.startsWith("pbkdf2s256:"))
        assertNotEquals(legacySha256("0000", salt), hash)
        assertNotEquals(legacyPbkdf2Sha1("0000", salt), hash)
        assertEquals(false, hash.contains("0000"))
    }

    /** The pre-PBKDF2 scheme, reproduced here so the compatibility path has something to test. */
    private fun legacySha256(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return Base64.getEncoder().encodeToString(digest.digest(pin.toByteArray(Charsets.UTF_8)))
    }

    /** The PBKDF2-SHA1 scheme [hashPin] used before moving to SHA-256, reproduced here so the
     * compatibility path has something to test. */
    private fun legacyPbkdf2Sha1(pin: String, salt: ByteArray): String {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return "pbkdf2:" + Base64.getEncoder().encodeToString(hash)
    }
}
