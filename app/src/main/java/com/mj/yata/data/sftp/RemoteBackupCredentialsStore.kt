package com.mj.yata.data.sftp

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the actual secrets a self-hosted backup connection needs — password, SFTP private key,
 * SFTP key passphrase — in their own [EncryptedSharedPreferences], never in the plain-text
 * DataStore [com.mj.yata.data.local.datastore.UserPreferences] uses for everything else (host,
 * port, username, remote path, protocol choice, TOFU host key fingerprint — none of that is
 * sensitive the way these are). Same [MasterKey] mechanism as [com.mj.yata.util.ProfilePhotoUtils]'s
 * `EncryptedFile`, just for a key-value store instead of a file.
 *
 * Shared between [SftpBackupManager] and [com.mj.yata.data.ftp.FtpBackupManager]: [password] is
 * the one either protocol reads, since the settings UI only ever has one of them configured at a
 * time (the protocol picker chooses which server the shared host/port/username/remote-dir fields
 * describe). [privateKeyPem]/[passphrase] are SFTP-only — FTP has no equivalent.
 *
 * Deliberately synchronous rather than Flow-based: these fields are write-only from the UI's
 * perspective (a password field the user types into and saves, never displayed back), so there's
 * no reactive-read use case that would justify the extra machinery a Flow wrapper adds.
 */
@Singleton
class RemoteBackupCredentialsStore @Inject constructor(@ApplicationContext context: Context) {

    private companion object {
        const val PREFS_FILE_NAME = "remote_backup_credentials"
        const val KEY_PASSWORD = "password"
        const val KEY_PRIVATE_KEY_PEM = "private_key_pem"
        const val KEY_PASSPHRASE = "passphrase"
        const val KEY_BACKUP_PASSPHRASE = "backup_passphrase"
        const val KEY_GITHUB_TOKEN = "github_token"
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

    /** The password for either protocol — SFTP password auth, or FTP/FTPS. */
    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(value) = prefs.edit { if (value != null) putString(KEY_PASSWORD, value) else remove(KEY_PASSWORD) }

    /** PEM-encoded private key text, as pasted or picked by the user — never the file's raw
     * content:// Uri, which (like a picked photo, see ProfilePhotoUtils' doc comment) is only a
     * one-time process-scoped grant and would go dead on relaunch. SFTP only. */
    var privateKeyPem: String?
        get() = prefs.getString(KEY_PRIVATE_KEY_PEM, null)
        set(value) = prefs.edit { if (value != null) putString(KEY_PRIVATE_KEY_PEM, value) else remove(KEY_PRIVATE_KEY_PEM) }

    /** SFTP only. */
    var passphrase: String?
        get() = prefs.getString(KEY_PASSPHRASE, null)
        set(value) = prefs.edit { if (value != null) putString(KEY_PASSPHRASE, value) else remove(KEY_PASSPHRASE) }

    /**
     * Passphrase the backup *file itself* is encrypted with before it leaves the device — distinct
     * from [password] (which authenticates to the server) and from [passphrase] (which unlocks an
     * SSH private key). Null means backups are uploaded unencrypted.
     *
     * Kept here so scheduled backups can run unattended, but unlike the other secrets this one is
     * unrecoverable-by-design: it never goes to the server, so a backup can only be restored by
     * someone who still knows it. Losing it loses the backups, which is why the settings UI says so
     * out loud.
     */
    var backupPassphrase: String?
        get() = prefs.getString(KEY_BACKUP_PASSPHRASE, null)
        set(value) = prefs.edit {
            if (!value.isNullOrBlank()) putString(KEY_BACKUP_PASSPHRASE, value) else remove(KEY_BACKUP_PASSPHRASE)
        }

    var githubToken: String?
        get() = prefs.getString(KEY_GITHUB_TOKEN, null)
        set(value) = prefs.edit {
            if (!value.isNullOrBlank()) putString(KEY_GITHUB_TOKEN, value) else remove(KEY_GITHUB_TOKEN)
        }

    /** Wipes all of them — called when the user disables/reconfigures self-hosted backup, so
     * switching protocol, auth method, or server doesn't leave a stale credential behind that
     * nothing references anymore but that's still sitting encrypted on disk. */
    fun clear() {
        prefs.edit { clear() }
    }
}
