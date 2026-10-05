package com.mj.yata.domain.model

/**
 * Which transport the self-hosted backup section talks. Both share the same host/port/username/
 * remote-directory fields when the user switches between them — a self-hosted user has one
 * server, not two — but each keeps its own auth details (SFTP's password-or-private-key vs.
 * FTP's password-only) and FTP additionally has [use TLS][com.mj.yata.data.local.datastore.
 * UserPreferences.ftpUseTlsFlow], since unlike SSH it has no encryption by default at all.
 */
enum class RemoteBackupProtocol {
    SFTP,
    FTP,
    GITHUB
}
