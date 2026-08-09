package com.mj.yata.data.github

import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class GitHubConfigTransferPayload(
    val owner: String,
    val repo: String,
    val branch: String,
    val apiBase: String,
    val token: String,
    val tokenExpiresAt: Long?,
    val backupPassphrase: String?
)

/**
 * Password-encrypted transfer format for moving GitHub sync credentials to another device.
 *
 * The exported file intentionally contains only a tiny plaintext envelope: type, version, KDF, salt,
 * IV, and ciphertext. Repo details, the GitHub token, and the optional backup encryption passphrase
 * live inside AES-GCM authenticated ciphertext derived from the user-entered transfer password.
 */
object GitHubConfigTransfer {

    const val DEFAULT_FILENAME = "yata-github-config.json"

    private const val FILE_TYPE = "com.mj.yata.github-config"
    private const val FORMAT_VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val CIPHER_LABEL = "AES-256-GCM"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 150_000
    private const val MIN_SUPPORTED_ITERATIONS = 100_000
    private const val MAX_SUPPORTED_ITERATIONS = 500_000
    private const val MAX_CIPHERTEXT_BYTES = 64 * 1024

    private val random = SecureRandom()
    private val base64: Base64.Encoder = Base64.getEncoder()
    private val base64Decoder: Base64.Decoder = Base64.getDecoder()

    fun encryptToJson(payload: GitHubConfigTransferPayload, password: String): String {
        require(password.isNotBlank()) { "Transfer password is required" }
        val normalized = payload.normalized()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt, PBKDF2_ITERATIONS),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        cipher.updateAAD(associatedData())
        val ciphertext = cipher.doFinal(normalized.toJson().toString().toByteArray(Charsets.UTF_8))

        return JSONObject()
            .put("type", FILE_TYPE)
            .put("version", FORMAT_VERSION)
            .put("kdf", KDF)
            .put("iterations", PBKDF2_ITERATIONS)
            .put("cipher", CIPHER_LABEL)
            .put("salt", base64.encodeToString(salt))
            .put("iv", base64.encodeToString(iv))
            .put("ciphertext", base64.encodeToString(ciphertext))
            .toString()
    }

    fun decryptFromJson(exportText: String, password: String): GitHubConfigTransferPayload {
        require(password.isNotBlank()) { "Transfer password is required" }
        val envelope = runCatching { JSONObject(exportText) }.getOrElse {
            throw IllegalArgumentException("Not a YATA GitHub config export", it)
        }
        require(envelope.optString("type") == FILE_TYPE) { "Not a YATA GitHub config export" }
        require(envelope.optInt("version") == FORMAT_VERSION) { "Unsupported GitHub config export version" }
        require(envelope.optString("kdf") == KDF) { "Unsupported GitHub config key format" }
        require(envelope.optString("cipher") == CIPHER_LABEL) { "Unsupported GitHub config cipher" }
        val iterations = envelope.optInt("iterations")
        require(iterations in MIN_SUPPORTED_ITERATIONS..MAX_SUPPORTED_ITERATIONS) {
            "Unsupported GitHub config key strength"
        }

        val salt = decodeBase64(envelope, "salt")
        val iv = decodeBase64(envelope, "iv")
        val ciphertext = decodeBase64(envelope, "ciphertext")
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES && ciphertext.size <= MAX_CIPHERTEXT_BYTES) {
            "Damaged GitHub config export"
        }
        val plain = try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt, iterations),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.updateAAD(associatedData())
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("Wrong password or damaged GitHub config export", e)
        }

        val payload = runCatching { JSONObject(String(plain, Charsets.UTF_8)) }.getOrElse {
            throw IllegalArgumentException("Damaged GitHub config export", it)
        }
        return GitHubConfigTransferPayload(
            owner = payload.getString("owner"),
            repo = payload.getString("repo"),
            branch = payload.optString("branch", "main"),
            apiBase = payload.optString("apiBase", "https://api.github.com"),
            token = payload.getString("token"),
            tokenExpiresAt = payload.optNullableLong("tokenExpiresAt"),
            backupPassphrase = payload.optNullableString("backupPassphrase")
        ).normalized()
    }

    private fun GitHubConfigTransferPayload.normalized(): GitHubConfigTransferPayload {
        val normalizedOwner = owner.trim()
        val normalizedRepo = repo.trim()
        val normalizedBranch = branch.trim().ifBlank { "main" }
        val normalizedApiBase = apiBase.trim().ifBlank { "https://api.github.com" }
        val normalizedToken = token.trim()
        require(normalizedOwner.isNotBlank()) { "GitHub owner is required" }
        require(normalizedRepo.isNotBlank()) { "GitHub repo is required" }
        require(normalizedToken.isNotBlank()) { "GitHub token is required" }
        return copy(
            owner = normalizedOwner,
            repo = normalizedRepo,
            branch = normalizedBranch,
            apiBase = normalizedApiBase,
            token = normalizedToken,
            backupPassphrase = backupPassphrase?.takeIf { it.isNotBlank() }
        )
    }

    private fun GitHubConfigTransferPayload.toJson(): JSONObject =
        JSONObject()
            .put("owner", owner)
            .put("repo", repo)
            .put("branch", branch)
            .put("apiBase", apiBase)
            .put("token", token)
            .put("tokenExpiresAt", tokenExpiresAt ?: JSONObject.NULL)
            .put("backupPassphrase", backupPassphrase ?: JSONObject.NULL)

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(KDF)
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun decodeBase64(envelope: JSONObject, key: String): ByteArray =
        runCatching { base64Decoder.decode(envelope.getString(key)) }.getOrElse {
            throw IllegalArgumentException("Damaged GitHub config export", it)
        }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun associatedData(): ByteArray =
        "$FILE_TYPE:v$FORMAT_VERSION".toByteArray(Charsets.UTF_8)
}
