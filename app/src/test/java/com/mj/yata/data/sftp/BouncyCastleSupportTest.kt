package com.mj.yata.data.sftp

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory

/**
 * Guards the two properties that made moving this off `Application.onCreate` worth doing. Both are
 * easy to undo by accident — `insertProviderAt(provider, 1)` looks like the obvious way to
 * register a provider, and is what this code did before.
 */
class BouncyCastleSupportTest {

    @Test
    fun ensureRegistered_registersTheBundledProvider() {
        BouncyCastleSupport.ensureRegistered()

        val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        assertNotNull("sshj needs a provider registered under the name BC", provider)
        assertEquals(
            "Must be the bundled implementation, not a stripped platform one under the same name",
            BouncyCastleProvider::class.java,
            provider!!.javaClass
        )
    }

    @Test
    fun ensureRegistered_isIdempotent() {
        BouncyCastleSupport.ensureRegistered()
        val countAfterFirst = Security.getProviders().count {
            it.name == BouncyCastleProvider.PROVIDER_NAME
        }
        BouncyCastleSupport.ensureRegistered()
        BouncyCastleSupport.ensureRegistered()

        assertEquals(
            "Repeat calls must not stack duplicate providers",
            countAfterFirst,
            Security.getProviders().count { it.name == BouncyCastleProvider.PROVIDER_NAME }
        )
    }

    @Test
    fun ensureRegistered_doesNotTakeOverAesGcmFromThePlatform() {
        // The regression this exists to catch: registering at slot 1 puts Bouncy Castle ahead of
        // the platform provider for every algorithm it implements, including AES/GCM -- which it
        // backs with a pure-Java AESEngine/GCMBlockCipher. On Android that silently costs
        // BackupCrypto/EncryptedFile/EncryptedSharedPreferences their hardware-accelerated
        // AES-GCM. Appending instead of inserting is what keeps the platform's implementation
        // winning; this asserts the resolution, not the call, so it fails if that flips back.
        BouncyCastleSupport.ensureRegistered()

        val gcmProvider = Cipher.getInstance("AES/GCM/NoPadding").provider.name
        assertTrue(
            "AES/GCM resolved to Bouncy Castle; it must stay with the platform provider",
            gcmProvider != BouncyCastleProvider.PROVIDER_NAME
        )
    }

    @Test
    fun ensureRegistered_doesNotTakeOverPbkdf2FromThePlatform() {
        // Same reasoning as AES/GCM, for the KDF behind the app-lock PIN (120k iterations on every
        // unlock) and backup passphrase derivation.
        BouncyCastleSupport.ensureRegistered()

        val kdfProvider = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").provider.name
        assertTrue(
            "PBKDF2 resolved to Bouncy Castle; it must stay with the platform provider",
            kdfProvider != BouncyCastleProvider.PROVIDER_NAME
        )
    }
}
