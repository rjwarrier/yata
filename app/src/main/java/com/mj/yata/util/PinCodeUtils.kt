package com.mj.yata.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Minimal salted-hash storage for the app-lock PIN — SHA-256 over salt+pin, not a heavier KDF
 * like PBKDF2/bcrypt, since this only gates local app-open (no network, no export of the hash),
 * a threat model that doesn't call for the extra dependency. */
fun generateSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

fun hashPin(pin: String, salt: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(salt)
    val hash = digest.digest(pin.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(hash)
}

fun encodeSalt(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)

fun decodeSalt(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)
