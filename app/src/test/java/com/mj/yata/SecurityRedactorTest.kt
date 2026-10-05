package com.mj.yata

import com.mj.yata.util.SecurityRedactor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityRedactorTest {
    @Test
    fun redactsCommonSecretsFromDiagnostics() {
        val input = """
            Authorization: Bearer ghp_1234567890abcdef
            password=super-secret
            privateKey: -----BEGIN PRIVATE KEY-----
            abc123
            -----END PRIVATE KEY-----
            url=https://example.com/yata?token=abc
            user=person@example.com
            path=D:\AntiGravity\yata\files\photo.jpg
        """.trimIndent()

        val redacted = SecurityRedactor.redact(input)

        assertFalse(redacted.contains("ghp_1234567890abcdef"))
        assertFalse(redacted.contains("super-secret"))
        assertFalse(redacted.contains("abc123"))
        assertFalse(redacted.contains("person@example.com"))
        assertFalse(redacted.contains("photo.jpg"))
        assertTrue(redacted.contains("[REDACTED"))
    }
}
