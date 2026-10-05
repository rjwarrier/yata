package com.mj.yata.util

/**
 * Best-effort redaction for diagnostics that can be copied, shared, or read from logcat.
 * Keep this intentionally pattern-based and dependency-free so it is safe inside crash handling.
 */
object SecurityRedactor {
    private val patterns = listOf(
        Regex("""(?i)(authorization:\s*(?:bearer|token)\s+)[^\s]+""") to "\$1[REDACTED]",
        Regex("""(?i)\b(gh[pousr]_[A-Za-z0-9_]+)\b""") to "[REDACTED_GITHUB_TOKEN]",
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----""") to "[REDACTED_PRIVATE_KEY]",
        Regex("""(?i)\b(password|passphrase|token|private[_ -]?key|backupPassphrase)\s*[:=]\s*([^\s,;]+)""") to "\$1=[REDACTED]",
        Regex("""(?i)(https?://)[^\s)]+""") to "$1[REDACTED_URL]",
        Regex("""(?i)\b[\w.%+-]+@[\w.-]+\.[a-z]{2,}\b""") to "[REDACTED_EMAIL]",
        Regex("""(?i)\b(?:[a-z]:\\|/)(?:[^:\n\r\t ]+[\\/])+[^:\n\r\t ]*""") to "[REDACTED_PATH]"
    )

    fun redact(text: String): String =
        patterns.fold(text) { current, (pattern, replacement) ->
            pattern.replace(current, replacement)
        }
}
