package com.mj.yata.data.sftp

import android.util.Log
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Registers the bundled Bouncy Castle provider, once, the first time SFTP actually needs it.
 *
 * sshj needs Bouncy Castle for algorithms Android's stock providers don't cover (Ed25519 keys,
 * curve25519-sha256 key exchange), which a lot of real-world OpenSSH servers default to. Android
 * ships an *incomplete* provider already using the name "BC", so checking the name alone would
 * leave that one in place and never register the bundled implementation — hence the class
 * comparison rather than a null check.
 *
 * Two deliberate choices here, both of which this used to get wrong from `Application.onCreate`:
 *
 * **Lazy, not at startup.** Constructing [BouncyCastleProvider] registers on the order of a
 * thousand algorithm mappings. Doing that on the main thread during `onCreate` charged every cold
 * start for a dependency only SFTP backup uses, which most installs never configure.
 *
 * **Appended, not inserted at slot 1.** `insertProviderAt(provider, 1)` puts Bouncy Castle at the
 * *front* of the JCE resolution chain, so it wins for every algorithm it implements — including
 * `AES/GCM/NoPadding`, which it backs with a pure-Java `AESEngine`/`GCMBlockCipher`. That silently
 * took AES-GCM away from the platform's hardware-accelerated implementation for
 * [com.mj.yata.util.BackupCrypto], `EncryptedFile`, `EncryptedSharedPreferences` and
 * [com.mj.yata.data.github.GitHubConfigTransfer], which is a real cost on a multi-megabyte backup.
 * sshj doesn't need the front slot: it resolves by provider *name* (`SecurityUtils.getCipher`,
 * `getKeyFactory`, `getSignature`, …), so merely being registered is enough.
 *
 * Provider choice can't change any stored value — PBKDF2 and AES-GCM are deterministic given the
 * same inputs — so existing PIN hashes and encrypted backups stay valid either way.
 */
internal object BouncyCastleSupport {

    private const val TAG = "BouncyCastleSupport"

    @Volatile
    private var registered = false

    /** Idempotent and safe to call from any thread; only the first call does work. */
    fun ensureRegistered() {
        if (registered) return
        synchronized(this) {
            if (registered) return
            runCatching { register() }
                .onFailure { Log.w(TAG, "Could not register the bundled Bouncy Castle provider", it) }
            // Set even on failure: a second attempt would fail the same way, and the SFTP
            // connection that triggered this reports the real error on its own.
            registered = true
        }
    }

    private fun register() {
        val bundled = BouncyCastleProvider()
        val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (existing?.javaClass == bundled.javaClass) return
        if (existing != null) {
            Security.removeProvider(existing.name)
        }
        Security.addProvider(bundled)
    }
}
