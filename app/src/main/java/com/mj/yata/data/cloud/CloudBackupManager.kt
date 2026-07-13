package com.mj.yata.data.cloud

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.mj.yata.data.local.datastore.UserPreferences
import com.mj.yata.domain.model.Task
import com.mj.yata.util.JsonExporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class CloudBackupEntry(val id: String, val name: String, val createdTime: String, val sizeBytes: Long?)

/** Titles are capped (see [CloudBackupManager.MAX_DIFF_TITLES]) for a readable dialog — the
 * `*Count` fields hold the true total so the UI can show "+N more" instead of silently
 * truncating without saying so. */
data class CloudBackupDiff(
    val currentPending: Int,
    val currentDone: Int,
    val backupPending: Int,
    val backupDone: Int,
    val backupCreatedTime: String,
    val addedTitles: List<String>,
    val addedCount: Int,
    val removedTitles: List<String>,
    val removedCount: Int,
    val changedTitles: List<String>,
    val changedCount: Int
) {
    val pendingDiff: Int get() = currentPending - backupPending
    val doneDiff: Int get() = currentDone - backupDone
}

/** Pure so it's usable straight from Compose without touching [CloudBackupManager] — `null`
 * (never backed up) counts as stale too, not just "old enough." */
fun isCloudBackupStale(lastAtMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean =
    lastAtMillis == null || nowMillis - lastAtMillis >= 7 * 24 * 60 * 60 * 1000L

sealed class CloudBackupError(message: String) : Exception(message) {
    object NotSignedIn : CloudBackupError("Not signed in to a Google account.")
    object NeedsReauth : CloudBackupError("Cloud backup needs re-authorization.")
    object WifiRequired : CloudBackupError("Waiting for Wi-Fi to back up.")
    object NoNetwork : CloudBackupError("No network connection.")
    class Api(message: String) : CloudBackupError(message)
}

/**
 * Backs the app's tasks up to the signed-in user's Google Drive `appDataFolder` — a hidden
 * per-app folder invisible in the user's normal Drive UI, so this never shows up as "a random
 * file the app dumped in my Drive." Reuses [JsonExporter]'s existing backup format; this class
 * only owns auth + the Drive REST v3 transport (deliberately raw OkHttp calls rather than the
 * full google-api-services-drive client, which pulls in a much heavier dependency graph for
 * three endpoints: multipart upload, list, get-media, delete).
 */
@Singleton
class CloudBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter,
    private val userPreferences: UserPreferences,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "CloudBackupManager"
        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val KEEP_BACKUPS = 5
        private const val DEBOUNCE_MILLIS = 2 * 60 * 1000L
        private const val MAX_DIFF_TITLES = 8

        // Single cumulative file, not a rotated series like the primary backups — replaced in
        // place whenever archived-completed-task content changes rather than pruned/kept-N.
        private const val ARCHIVE_FILENAME = "yata_archive.json"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    fun signInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun signInIntent(): Intent = signInClient().signInIntent

    fun currentAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    /** [currentAccount] plus a self-heal: if Play Services no longer has a cached account
     * (revoked, cleared, expired test-user grant — anything that drops it independently of this
     * app), the DataStore "signed in as X" flag would otherwise sit there forever showing a
     * state that can never actually back up. Clearing it here means Settings falls back to a
     * "Sign in" button instead of a stuck, misleading "Signed in as X."
     *
     * [GoogleSignIn.getLastSignedInAccount] alone is NOT trustworthy enough to gate that clear —
     * it's a synchronous cached read that can spuriously return null in a freshly-spawned
     * headless process (e.g. this is called from [CloudBackupWorker] on a background WorkManager
     * run with no warm Activity/GoogleApiClient), even though the account is still genuinely
     * signed in system-wide. That previously caused the account to look "logged out" mid
     * automatic-backup. [silentSignIn] does an authoritative, awaited recheck against Play
     * Services before this concludes the sign-in is actually gone. */
    private suspend fun requireAccount(): GoogleSignInAccount? {
        val cached = currentAccount()
        if (cached != null) return cached
        if (userPreferences.cloudBackupAccountEmailFlow.first() == null) return null

        return try {
            Tasks.await(signInClient().silentSignIn())
        } catch (e: Exception) {
            Log.w(TAG, "requireAccount: silent re-auth failed, clearing stale sign-in state", e)
            userPreferences.setCloudBackupAccountEmail(null)
            null
        }
    }

    /** Call from the sign-in [ActivityResultLauncher] callback with the returned [Intent]. */
    suspend fun handleSignInResult(data: Intent?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
            val email = account.email ?: "signed-in account"
            userPreferences.setCloudBackupAccountEmail(email)
            userPreferences.setCloudBackupEnabled(true)
            Result.success(email)
        } catch (e: ApiException) {
            Log.w(TAG, "handleSignInResult failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            Tasks.await(signInClient().signOut())
        } catch (e: Exception) {
            Log.w(TAG, "signOut failed", e)
        }
        userPreferences.setCloudBackupEnabled(false)
        userPreferences.setCloudBackupAccountEmail(null)
    }

    /** Applies a new periodic-backup cadence immediately — UPDATE policy so it actually
     * reschedules the already-enqueued periodic work rather than a no-op (KEEP would leave the
     * old interval running until the app's next cold start). */
    fun updateBackupInterval(intervalMinutes: Long) {
        scope.launch {
            val wifiOnly = userPreferences.cloudBackupWifiOnlyFlow.first()
            CloudBackupWorker.schedule(context, intervalMinutes, androidx.work.ExistingPeriodicWorkPolicy.UPDATE, wifiOnly)
        }
    }

    /** Debounced trigger for the "something changed" hook — cancels any pending backup and
     * schedules a new one [DEBOUNCE_MILLIS] out, so a burst of edits produces one upload instead
     * of one per keystroke/toggle. */
    fun scheduleDebouncedBackup() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            if (userPreferences.cloudBackupEnabledFlow.first()) {
                backupNow()
            }
        }
    }

    suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
        val account = requireAccount() ?: return@withContext Result.failure(CloudBackupError.NotSignedIn)

        val wifiOnly = userPreferences.cloudBackupWifiOnlyFlow.first()
        if (!hasUsableNetwork(wifiOnly)) {
            return@withContext Result.failure(if (wifiOnly) CloudBackupError.WifiRequired else CloudBackupError.NoNetwork)
        }

        val token = getAccessToken(account) ?: return@withContext Result.failure(CloudBackupError.NeedsReauth)

        try {
            val archiveMonths = userPreferences.cloudBackupArchiveMonthsFlow.first()
            val (primaryJson, archiveJson) = jsonExporter.buildSplitBackupJson(archiveMonths)
            val primaryBytes = primaryJson.toString(2).toByteArray(Charsets.UTF_8)
            val filename = "yata_backup_" +
                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) +
                ".json"

            uploadFile(token, filename, primaryBytes).getOrElse { e ->
                Log.w(TAG, "backupNow: primary upload failed", e)
                return@withContext Result.failure(e)
            }

            // Best-effort — the archive is a convenience split, not the source of truth (nothing
            // was removed from the live DB), so a failure here shouldn't fail the whole backup.
            if (archiveJson != null) {
                try {
                    replaceArchiveFile(token, archiveJson.toString(2).toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.w(TAG, "backupNow: archive upload failed, primary backup still succeeded", e)
                }
            }

            userPreferences.setCloudBackupLastAt(System.currentTimeMillis())
            pruneOldBackups(token)
            Result.success(Unit)
        } catch (e: IOException) {
            Log.w(TAG, "backupNow failed", e)
            Result.failure(e)
        }
    }

    private fun uploadFile(token: String, filename: String, bytes: ByteArray): Result<Unit> {
        val metadata = JSONObject().apply {
            put("name", filename)
            put("parents", JSONArray().put("appDataFolder"))
        }.toString()

        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/json".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("$DRIVE_UPLOAD_URL?uploadType=multipart&fields=id")
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val reason = driveErrorMessage(response)
                Log.w(TAG, "uploadFile($filename): HTTP ${response.code} — $reason")
                return Result.failure(CloudBackupError.Api("Upload failed: HTTP ${response.code} — $reason"))
            }
        }
        return Result.success(Unit)
    }

    /** [ARCHIVE_FILENAME] is a single logical file, not a rotated series — delete whatever's
     * there under that name (if anything) before uploading the replacement, since Drive's simple
     * multipart-create endpoint doesn't do in-place content updates. */
    private fun replaceArchiveFile(token: String, bytes: ByteArray) {
        findFileIdByName(ARCHIVE_FILENAME, token)?.let { existingId ->
            val request = Request.Builder()
                .url("$DRIVE_FILES_URL/$existingId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            httpClient.newCall(request).execute().close()
        }
        uploadFile(token, ARCHIVE_FILENAME, bytes).getOrThrow()
    }

    private fun findFileIdByName(name: String, token: String): String? {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL?spaces=appDataFolder&q=" + java.net.URLEncoder.encode("name='$name'", "UTF-8") + "&fields=files(id)")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: "{}")
            val files = json.optJSONArray("files") ?: JSONArray()
            return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
    }

    suspend fun listBackups(): Result<List<CloudBackupEntry>> = withContext(Dispatchers.IO) {
        val account = requireAccount() ?: return@withContext Result.failure(CloudBackupError.NotSignedIn)
        val token = getAccessToken(account) ?: return@withContext Result.failure(CloudBackupError.NeedsReauth)
        try {
            Result.success(fetchBackupList(token))
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val account = requireAccount() ?: return@withContext Result.failure(CloudBackupError.NotSignedIn)
        val token = getAccessToken(account) ?: return@withContext Result.failure(CloudBackupError.NeedsReauth)
        try {
            val bytes = downloadBackupBytes(fileId, token)
            if (!jsonExporter.importBytes(bytes)) {
                return@withContext Result.failure(CloudBackupError.Api("Restore parsing failed"))
            }

            // The primary backup may have had old completed tasks split out of it — pull the
            // archive back in too (best-effort: a missing/corrupt archive shouldn't fail a
            // restore that already succeeded for everything else).
            try {
                findFileIdByName(ARCHIVE_FILENAME, token)?.let { archiveId ->
                    jsonExporter.importBytes(downloadBackupBytes(archiveId, token))
                }
            } catch (e: Exception) {
                Log.w(TAG, "restoreBackup: archive merge failed, primary restore still succeeded", e)
            }

            Result.success(Unit)
        } catch (e: IOException) {
            Log.w(TAG, "restoreBackup failed", e)
            Result.failure(e)
        }
    }

    /** Read-only comparison against the most recent cloud backup — never touches local data,
     * unlike [restoreBackup]. [currentTasks] should already exclude trashed tasks (same as what
     * a fresh backup would contain) so the counts are apples-to-apples. */
    suspend fun compareWithLatestBackup(currentTasks: List<Task>): Result<CloudBackupDiff> = withContext(Dispatchers.IO) {
        val account = requireAccount() ?: return@withContext Result.failure(CloudBackupError.NotSignedIn)
        val token = getAccessToken(account) ?: return@withContext Result.failure(CloudBackupError.NeedsReauth)
        try {
            val latest = fetchBackupList(token).firstOrNull()
                ?: return@withContext Result.failure(CloudBackupError.Api("No cloud backups found yet"))
            val bytes = downloadBackupBytes(latest.id, token)
            val tasksArr = JSONObject(String(bytes, Charsets.UTF_8)).optJSONArray("tasks") ?: JSONArray()

            // The primary backup may have split old completed tasks out into the archive file —
            // without folding those back in, every archived task would misleadingly show up as
            // "removed since backup" just because it's no longer in the primary payload.
            val archiveTasksArr = try {
                findFileIdByName(ARCHIVE_FILENAME, token)
                    ?.let { archiveId -> JSONObject(String(downloadBackupBytes(archiveId, token), Charsets.UTF_8)).optJSONArray("tasks") }
            } catch (e: Exception) {
                Log.w(TAG, "compareWithLatestBackup: couldn't read archive, comparing against primary only", e)
                null
            } ?: JSONArray()
            for (i in 0 until archiveTasksArr.length()) {
                tasksArr.put(archiveTasksArr.getJSONObject(i))
            }

            var backupDone = 0
            var backupPending = 0
            // id -> (title, done), so added/removed/changed below can diff by id rather than by
            // title (titles aren't unique, ids are).
            val backupById = HashMap<String, Pair<String, Boolean>>(tasksArr.length())
            for (i in 0 until tasksArr.length()) {
                val o = tasksArr.getJSONObject(i)
                val done = o.optBoolean("done", false)
                if (done) backupDone++ else backupPending++
                backupById[o.getString("id")] = o.getString("title") to done
            }

            val currentById = currentTasks.associateBy { it.id }
            val currentDone = currentTasks.count { it.done }

            val addedTitles = currentById.keys.minus(backupById.keys).map { currentById.getValue(it).title }
            val removedTitles = backupById.keys.minus(currentById.keys).map { backupById.getValue(it).first }
            val changedTitles = currentById.keys.intersect(backupById.keys).mapNotNull { id ->
                val current = currentById.getValue(id)
                val backup = backupById.getValue(id)
                if (current.title != backup.first || current.done != backup.second) current.title else null
            }

            Result.success(
                CloudBackupDiff(
                    currentPending = currentTasks.size - currentDone,
                    currentDone = currentDone,
                    backupPending = backupPending,
                    backupDone = backupDone,
                    backupCreatedTime = latest.createdTime,
                    addedTitles = addedTitles.take(MAX_DIFF_TITLES),
                    addedCount = addedTitles.size,
                    removedTitles = removedTitles.take(MAX_DIFF_TITLES),
                    removedCount = removedTitles.size,
                    changedTitles = changedTitles.take(MAX_DIFF_TITLES),
                    changedCount = changedTitles.size
                )
            )
        } catch (e: IOException) {
            Log.w(TAG, "compareWithLatestBackup failed", e)
            Result.failure(e)
        }
    }

    private fun downloadBackupBytes(fileId: String, token: String): ByteArray {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL/$fileId?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val reason = driveErrorMessage(response)
                Log.w(TAG, "downloadBackupBytes: HTTP ${response.code} — $reason")
                throw IOException("Download failed: HTTP ${response.code} — $reason")
            }
            return response.body?.bytes() ?: throw IOException("Empty response")
        }
    }

    private fun fetchBackupList(token: String): List<CloudBackupEntry> {
        val request = Request.Builder()
            .url("$DRIVE_FILES_URL?spaces=appDataFolder&fields=files(id,name,createdTime,size)&orderBy=createdTime desc&pageSize=20")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val reason = driveErrorMessage(response)
                Log.w(TAG, "fetchBackupList failed HTTP ${response.code} — $reason")
                throw IOException("List failed: HTTP ${response.code} — $reason")
            }
            val json = JSONObject(response.body?.string() ?: "{}")
            val filesArr = json.optJSONArray("files") ?: JSONArray()
            return (0 until filesArr.length()).map { i ->
                val o = filesArr.getJSONObject(i)
                CloudBackupEntry(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    createdTime = o.getString("createdTime"),
                    sizeBytes = o.optString("size", null)?.toLongOrNull()
                )
            }
        }
    }

    private fun pruneOldBackups(token: String) {
        val backups = try {
            fetchBackupList(token)
        } catch (e: IOException) {
            Log.w(TAG, "pruneOldBackups: couldn't list backups", e)
            return
        }
        backups.drop(KEEP_BACKUPS).forEach { entry ->
            try {
                val request = Request.Builder()
                    .url("$DRIVE_FILES_URL/${entry.id}")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                httpClient.newCall(request).execute().close()
            } catch (e: IOException) {
                Log.w(TAG, "pruneOldBackups: failed to delete ${entry.id}", e)
            }
        }
    }

    private fun getAccessToken(account: GoogleSignInAccount): String? {
        val androidAccount = account.account ?: return null
        return try {
            GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DRIVE_APPDATA_SCOPE")
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "getAccessToken: needs user consent", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "getAccessToken failed", e)
            null
        }
    }

    /** Drive's JSON error body has the actual reason (e.g. "Google Drive API has not been
     * used in project ... before or it is disabled", "Insufficient Permission") — the bare
     * HTTP status alone isn't enough to diagnose a 403/400 from Settings' error toast. */
    private fun driveErrorMessage(response: okhttp3.Response): String {
        val raw = try { response.body?.string() } catch (e: IOException) { null } ?: return "no response body"
        return try {
            JSONObject(raw).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: raw.take(300)
        } catch (e: Exception) {
            raw.take(300)
        }
    }

    private fun hasUsableNetwork(wifiOnly: Boolean): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (wifiOnly && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return false
        return true
    }
}
