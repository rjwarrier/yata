package com.mj.yata.data.sftp

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the three actual secrets an SFTP connection needs — password, private key, key
 * passphrase — in their own [EncryptedSharedPreferences], never in the plain-text DataStore
 * [com.mj.yata.data.local.datastore.UserPreferences] uses for everything else (host, port,
 * username, remote path, TOFU host key fingerprint — none of that is sensitive the way these
 * are). Same [MasterKey] mechanism as [com.mj.yata.util.ProfilePhotoUtils]'s `EncryptedFile`,
 * just for a key-value store instead of a file.
 *
 * Deliberately synchronous rather than Flow-based: these fields are write-only from the UI's
 * perspective (a password field the user types into and saves, never displayed back), so there's
 * no reactive-read use case that would justify the extra machinery a Flow wrapper adds.
 */
@Singleton
class SftpCredentialsStore @Inject constructor(@ApplicationContext context: Context) {

    private companion object {
        const val PREFS_FILE_NAME = "sftp_credentials"
        const val KEY_PASSWORD = "password"
        const val KEY_PRIVATE_KEY_PEM = "private_key_pem"
        const val KEY_PASSPHRASE = "passphrase"
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(value) = prefs.edit { if (value != null) putString(KEY_PASSWORD, value) else remove(KEY_PASSWORD) }

    /** PEM-encoded private key text, as pasted or picked by the user — never the file's raw
     * content:// Uri, which (like a picked photo, see ProfilePhotoUtils' doc comment) is only a
     * one-time process-scoped grant and would go dead on relaunch. */
    var privateKeyPem: String?
        get() = prefs.getString(KEY_PRIVATE_KEY_PEM, null)
        set(value) = prefs.edit { if (value != null) putString(KEY_PRIVATE_KEY_PEM, value) else remove(KEY_PRIVATE_KEY_PEM) }

    var passphrase: String?
        get() = prefs.getString(KEY_PASSPHRASE, null)
        set(value) = prefs.edit { if (value != null) putString(KEY_PASSPHRASE, value) else remove(KEY_PASSPHRASE) }

    /** Wipes all three — called when the user disables/reconfigures SFTP backup, so switching
     * auth method or server doesn't leave a stale credential behind that nothing references
     * anymore but that's still sitting encrypted on disk. */
    fun clear() {
        prefs.edit { clear() }
    }
}
