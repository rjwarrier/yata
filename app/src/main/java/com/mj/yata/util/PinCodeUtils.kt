package com.mj.yata.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted-hash storage for the app-lock PIN.
 *
 * A PIN has almost no entropy — a 4-digit one is ten thousand guesses — so the cost of *one* guess
 * is the only thing standing between a leaked preferences file and the PIN. The original
 * single-round SHA-256 here made that cost microseconds, meaning the whole keyspace fell in well
 * under a second. PBKDF2 with a deliberate iteration count is the point: it doesn't make the PIN
 * stronger, it makes each attempt expensive enough that exhausting the space stops being free.
 *
 * Hashes carry an algorithm prefix so every scheme this file has ever used can coexist:
 * `pbkdf2s256:` (current, PBKDF2WithHmacSHA256), `pbkdf2:` (the previous scheme,
 * PBKDF2WithHmacSHA1 — the weaker MAC doesn't meaningfully undermine PBKDF2's cost, but SHA-256 is
 * the more conservative default everywhere else in this codebase uses, and costs nothing extra to
 * match), and no prefix at all (the original single-round SHA-256, from before PBKDF2 existed
 * here). A PIN on any older scheme still verifies and is transparently re-hashed onto the current
 * one on the next successful unlock — see `UserPreferences.verifyAppLockPin`. Nothing re-prompts
 * the user, and nobody gets locked out by an upgrade.
 */
private const val PBKDF2_SHA256_PREFIX = "pbkdf2s256:"
private const val PBKDF2_SHA1_PREFIX = "pbkdf2:"
private const val PBKDF2_ITERATIONS = 120_000
private const val PBKDF2_KEY_LENGTH_BITS = 256

fun generateSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

/** The current scheme. Always used for newly set PINs. */
fun hashPin(pin: String, salt: ByteArray): String =
    PBKDF2_SHA256_PREFIX + pbkdf2Hash(pin, salt, "PBKDF2WithHmacSHA256")

/** The scheme [hashPin] used before moving off SHA-1. Only ever used to verify an existing PIN. */
private fun legacyPbkdf2Sha1HashPin(pin: String, salt: ByteArray): String =
    PBKDF2_SHA1_PREFIX + pbkdf2Hash(pin, salt, "PBKDF2WithHmacSHA1")

private fun pbkdf2Hash(pin: String, salt: ByteArray, algorithm: String): String {
    val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
    val hash = try {
        SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
    } finally {
        // The PIN's char[] copy inside the spec would otherwise sit in the heap, uncleared, for
        // however long the GC takes to reclaim it.
        spec.clearPassword()
    }
    return Base64.getEncoder().encodeToString(hash)
}

/** The scheme used before any PBKDF2 existed. Only ever used to verify an existing PIN. */
private fun legacyHashPin(pin: String, salt: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    return Base64.getEncoder().encodeToString(digest.digest(pin.toByteArray(Charsets.UTF_8)))
}

/**
 * True when [pin] matches [storedHash], under whichever scheme that hash was written with.
 *
 * The comparison is constant-time. A `==` on the Base64 strings returns as soon as two characters
 * differ, and the time it took to say no is a measurement of how many leading characters were
 * right — which, repeated, recovers the hash a character at a time.
 */
fun verifyPin(pin: String, storedHash: String, salt: ByteArray): Boolean {
    val candidate = when {
        storedHash.startsWith(PBKDF2_SHA256_PREFIX) -> hashPin(pin, salt)
        storedHash.startsWith(PBKDF2_SHA1_PREFIX) -> legacyPbkdf2Sha1HashPin(pin, salt)
        else -> legacyHashPin(pin, salt)
    }
    return constantTimeEquals(candidate, storedHash)
}

/** True when [storedHash] predates the current scheme and should be rewritten after a successful verify. */
fun needsRehash(storedHash: String): Boolean = !storedHash.startsWith(PBKDF2_SHA256_PREFIX)

private fun constantTimeEquals(a: String, b: String): Boolean {
    val aBytes = a.toByteArray(Charsets.UTF_8)
    val bBytes = b.toByteArray(Charsets.UTF_8)
    // MessageDigest.isEqual is the platform's constant-time comparison; it also handles the
    // length mismatch without short-circuiting on it.
    return MessageDigest.isEqual(aBytes, bBytes)
}

fun encodeSalt(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)

fun decodeSalt(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)
