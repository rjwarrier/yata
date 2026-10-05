package com.mj.yata.ui.screen.remotesync

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.data.github.GitHubConfigTransfer
import com.mj.yata.domain.model.RemoteBackupProtocol
import com.mj.yata.domain.sync.RestorePoint
import com.mj.yata.domain.sync.SyncCommitMessage
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.widgets.ContextualHelpButton
import com.mj.yata.ui.widgets.ContextualHelpTopic
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.YataCompactFieldShape
import com.mj.yata.ui.widgets.YataFieldShape
import com.mj.yata.ui.widgets.yataFieldColors
import com.mj.yata.util.localized
import com.mj.yata.util.modelSyncDeviceLabel
import com.mj.yata.util.syncDeviceLabel
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen remote sync configuration — provider picker, credentials, and test/connect. Used to
 * be an `AlertDialog` launched from Settings, but it had grown to cover three providers' worth of
 * fields plus connection testing; a dedicated destination gives it room and a normal back-button
 * dismiss instead of a scrolling modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteSyncScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSyncHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val remoteBackupProtocol = uiState.remoteBackupProtocol
    val sftpHost = uiState.sftpHost
    val sftpPort = uiState.sftpPort
    val sftpUsername = uiState.sftpUsername
    val sftpAuthMethod = uiState.sftpAuthMethod
    val sftpRemoteDir = uiState.sftpRemoteDir
    val sftpHostKeyFingerprint = uiState.sftpHostKeyFingerprint
    val ftpUseTls = uiState.ftpUseTls
    val ftpStrictTls = uiState.ftpStrictTls
    val githubOwner = uiState.githubOwner
    val githubRepo = uiState.githubRepo
    val githubBranch = uiState.githubBranch
    val githubApiBase = uiState.githubApiBase
    val githubTokenExpiresAt = uiState.githubTokenExpiresAt

    var draftProtocol by remember { mutableStateOf(remoteBackupProtocol) }
    var draftHost by remember { mutableStateOf(sftpHost) }
    var draftPort by remember { mutableStateOf(sftpPort.toString()) }
    var draftUsername by remember { mutableStateOf(sftpUsername) }
    var draftRemoteDir by remember { mutableStateOf(sftpRemoteDir) }
    var draftAuthMethod by remember { mutableStateOf(sftpAuthMethod) }
    var draftPrivateKey by remember { mutableStateOf("") }
    var draftFtpUseTls by remember { mutableStateOf(ftpUseTls) }
    var draftFtpStrictTls by remember { mutableStateOf(ftpStrictTls) }
    var draftGitHubRepo by remember { mutableStateOf(listOf(githubOwner, githubRepo).filter { it.isNotBlank() }.joinToString("/")) }
    var draftGitHubBranch by remember { mutableStateOf(githubBranch.ifBlank { "main" }) }
    var draftGitHubApiBase by remember { mutableStateOf(githubApiBase) }
    val passwordAlreadySet = remember { viewModel.hasRemoteBackupPassword() }
    var githubTokenAlreadySet by remember { mutableStateOf(viewModel.hasGitHubToken()) }
    val keyPassphraseAlreadySet = remember { viewModel.hasSftpKeyPassphrase() }
    var backupPassphraseAlreadySet by remember { mutableStateOf(viewModel.hasRemoteBackupPassphrase()) }
    val savedSecretPlaceholder = "...."
    // Secret fields are otherwise write-only from the UI's point of view -- typing nothing means
    // "leave as-is". Pre-filling with the placeholder itself (rather than relying on TextField's
    // `placeholder` slot, which only renders while focused) is what actually makes an already-saved
    // secret look saved at rest instead of indistinguishable from empty.
    var draftGitHubToken by remember {
        mutableStateOf(if (githubTokenAlreadySet) savedSecretPlaceholder else "")
    }
    var draftPassword by remember {
        mutableStateOf(if (passwordAlreadySet) savedSecretPlaceholder else "")
    }
    var draftPassphrase by remember {
        mutableStateOf(if (keyPassphraseAlreadySet) savedSecretPlaceholder else "")
    }
    var draftBackupPassphrase by remember {
        mutableStateOf(if (backupPassphraseAlreadySet) savedSecretPlaceholder else "")
    }
    var showGitHubPatHelpDialog by remember { mutableStateOf(false) }
    var gitHubConfigTransferMode by remember { mutableStateOf<GitHubConfigTransferMode?>(null) }
    var showGitHubForceDownloadDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var isTransferringGitHubConfig by remember { mutableStateOf(false) }
    var isDownloadingGitHubSnapshot by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    // null = untested this session, true/false = last test's outcome. A successful SFTP test
    // with no fingerprint pinned yet, or a failed one where the failure is a host-key
    // mismatch, both surface a trust prompt via pendingTrustFingerprint instead of a plain
    // result line. FTP/FTPS has no equivalent -- pendingTrustFingerprint stays null there and
    // every test outcome goes straight to testResultMessage.
    var testResultOk by remember { mutableStateOf<Boolean?>(null) }
    var testResultMessage by remember { mutableStateOf<String?>(null) }
    var pendingTrustFingerprint by remember { mutableStateOf<String?>(null) }
    var isHostKeyMismatch by remember { mutableStateOf(false) }
    val draftIsFtp = draftProtocol == RemoteBackupProtocol.FTP
    val draftIsGitHub = draftProtocol == RemoteBackupProtocol.GITHUB

    // Sync activity feed. Reads the *saved* config rather than the draft: it reports what has
    // actually been pushed to the connected repo, which an unsaved edit hasn't changed.
    var syncActivity by remember { mutableStateOf<List<RestorePoint>>(emptyList()) }
    var isLoadingSyncActivity by remember { mutableStateOf(false) }
    var syncActivityError by remember { mutableStateOf<String?>(null) }
    val githubConfigured = remoteBackupProtocol == RemoteBackupProtocol.GITHUB &&
        githubOwner.isNotBlank() && githubRepo.isNotBlank() && githubTokenAlreadySet
    // Both forms: snapshots pushed before the device was renamed (or before the Settings name was
    // preferred at all) carry the model-derived label, and should still read as "this device".
    val thisDeviceLabels = remember(context) {
        setOf(context.syncDeviceLabel(), modelSyncDeviceLabel())
    }

    fun loadSyncActivity() {
        if (isLoadingSyncActivity) return
        isLoadingSyncActivity = true
        syncActivityError = null
        // limit = 1: only the latest is ever shown here, so there's no reason to pay for a full
        // page (up to 100 commits) of GitHub API history just to read the first entry off it.
        viewModel.listRemoteRestorePoints(limit = 1) { result ->
            isLoadingSyncActivity = false
            result.fold(
                onSuccess = { points -> syncActivity = points },
                onFailure = { error ->
                    syncActivity = emptyList()
                    syncActivityError = error.message ?: context.getString(R.string.export_failed)
                }
            )
        }
    }

    // Reloads when the connected repo changes, so the feed can never show another repo's history.
    LaunchedEffect(githubConfigured, githubOwner, githubRepo, githubBranch) {
        if (githubConfigured) loadSyncActivity()
    }
    val tokenExpiryStatus = githubTokenExpiryStatus(githubTokenExpiresAt)
    fun enteredGitHubToken(): String =
        draftGitHubToken.takeUnless { githubTokenAlreadySet && it == savedSecretPlaceholder }.orEmpty()
    fun enteredPassword(): String =
        draftPassword.takeUnless { passwordAlreadySet && it == savedSecretPlaceholder }.orEmpty()
    fun enteredKeyPassphrase(): String =
        draftPassphrase.takeUnless { keyPassphraseAlreadySet && it == savedSecretPlaceholder }.orEmpty()
    fun enteredBackupPassphrase(): String =
        draftBackupPassphrase.takeUnless { backupPassphraseAlreadySet && it == savedSecretPlaceholder }.orEmpty()
    fun hasRequiredBackupPassphrase(): Boolean =
        backupPassphraseAlreadySet || enteredBackupPassphrase().isNotBlank()
    fun showBackupPassphraseRequired() {
        testResultOk = false
        testResultMessage = context.getString(R.string.remote_sync_backup_passphrase_required)
        isTestingConnection = false
        isDownloadingGitHubSnapshot = false
    }
    fun requireBackupPassphrase(): Boolean {
        if (hasRequiredBackupPassphrase()) return true
        showBackupPassphraseRequired()
        return false
    }
    fun persistBackupPassphraseDraft() {
        enteredBackupPassphrase().takeIf { it.isNotBlank() }?.let { passphrase ->
            viewModel.setRemoteBackupPassphrase(passphrase)
            backupPassphraseAlreadySet = true
            draftBackupPassphrase = savedSecretPlaceholder
        }
    }

    val createGitHubConfigExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val password = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && password != null) {
            scope.launch {
                testResultOk = null
                testResultMessage = null
                isTransferringGitHubConfig = true
                val writeResult = try {
                    val exportResult = viewModel.exportGitHubConfiguration(password)
                    if (exportResult.isSuccess) {
                        runCatching { writeTextToUri(context, uri, exportResult.getOrThrow()) }
                    } else {
                        Result.failure(exportResult.exceptionOrNull() ?: IllegalStateException(context.getString(R.string.export_failed)))
                    }
                } finally {
                    isTransferringGitHubConfig = false
                }
                testResultOk = writeResult.isSuccess
                testResultMessage = if (writeResult.isSuccess) {
                    context.getString(R.string.remote_sync_github_config_exported)
                } else {
                    writeResult.exceptionOrNull()?.message ?: context.getString(R.string.export_failed)
                }
            }
        }
    }

    val openGitHubConfigImport = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            gitHubConfigTransferMode = GitHubConfigTransferMode.IMPORT
        }
    }

    fun parseGitHubRepoDraft(): Pair<String, String>? {
        val parts = draftGitHubRepo.trim().split("/", limit = 2)
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            parts[0] to parts[1]
        } else {
            null
        }
    }

    fun saveServerConfiguration(onSaved: () -> Unit = {}) {
        if (!requireBackupPassphrase()) return
        persistBackupPassphraseDraft()
        if (draftIsGitHub) {
            enteredGitHubToken().takeIf { it.isNotBlank() }?.let { token ->
                viewModel.setGitHubToken(token)
                githubTokenAlreadySet = true
                draftGitHubToken = savedSecretPlaceholder
            }
            val repoParts = parseGitHubRepoDraft()
            if (repoParts != null) {
                viewModel.saveGitHubConfiguration(
                    owner = repoParts.first,
                    repo = repoParts.second,
                    branch = draftGitHubBranch,
                    apiBase = draftGitHubApiBase,
                    onSaved = onSaved
                )
            } else {
                testResultOk = false
                testResultMessage = "Enter the repo as owner/name"
                isTestingConnection = false
            }
            return
        }
        if (draftIsFtp) {
            enteredPassword().takeIf { it.isNotBlank() }?.let(viewModel::setSftpPassword)
        } else {
            viewModel.setSftpAuthMethod(draftAuthMethod)
            if (draftAuthMethod == "PRIVATE_KEY") {
                val enteredPassphrase = enteredKeyPassphrase()
                if (draftPrivateKey.isNotBlank() || enteredPassphrase.isNotBlank()) {
                    viewModel.setSftpPrivateKey(draftPrivateKey, enteredPassphrase)
                }
            } else {
                enteredPassword().takeIf { it.isNotBlank() }?.let(viewModel::setSftpPassword)
            }
        }
        viewModel.saveRemoteBackupConfiguration(
            protocol = draftProtocol,
            useTls = draftFtpUseTls,
            strictTls = draftFtpStrictTls,
            host = draftHost,
            port = draftPort.toIntOrNull() ?: sftpPort,
            username = draftUsername,
            remoteDir = draftRemoteDir,
            authMethod = draftAuthMethod,
            onSaved = onSaved
        )
    }

    fun requestGitHubConfigExport() {
        if (parseGitHubRepoDraft() == null) {
            testResultOk = false
            testResultMessage = "Enter the repo as owner/name"
            return
        }
        if (!githubTokenAlreadySet && enteredGitHubToken().isBlank()) {
            testResultOk = false
            testResultMessage = context.getString(R.string.remote_sync_github_config_export_missing)
            return
        }
        gitHubConfigTransferMode = GitHubConfigTransferMode.EXPORT
    }

    fun beginGitHubConfigExport(password: String) {
        val repoParts = parseGitHubRepoDraft()
        if (repoParts == null) {
            testResultOk = false
            testResultMessage = "Enter the repo as owner/name"
            return
        }
        if (!githubTokenAlreadySet && enteredGitHubToken().isBlank()) {
            testResultOk = false
            testResultMessage = context.getString(R.string.remote_sync_github_config_export_missing)
            return
        }
        saveServerConfiguration {
            pendingExportPassword = password
            createGitHubConfigExport.launch(GitHubConfigTransfer.DEFAULT_FILENAME)
        }
    }

    fun chooseGitHubConfigImportFile() {
        openGitHubConfigImport.launch(arrayOf("application/json", "text/*", "application/octet-stream", "*/*"))
    }

    fun importGitHubConfig(password: String) {
        val uri = pendingImportUri
        pendingImportUri = null
        if (uri == null) {
            testResultOk = false
            testResultMessage = context.getString(R.string.remote_sync_github_config_import_missing_file)
            return
        }
        scope.launch {
            testResultOk = null
            testResultMessage = null
            isTransferringGitHubConfig = true
            val importResult = try {
                val readResult = runCatching { readTextFromUri(context, uri) }
                if (readResult.isSuccess) {
                    viewModel.importGitHubConfiguration(readResult.getOrThrow(), password)
                } else {
                    Result.failure(readResult.exceptionOrNull() ?: IllegalStateException(context.getString(R.string.export_failed)))
                }
            } finally {
                isTransferringGitHubConfig = false
            }
            importResult
                .onSuccess { summary ->
                    draftProtocol = RemoteBackupProtocol.GITHUB
                    draftGitHubRepo = summary.repoLabel
                    draftGitHubBranch = summary.branch
                    draftGitHubApiBase = summary.apiBase
                    githubTokenAlreadySet = true
                    draftGitHubToken = savedSecretPlaceholder
                    backupPassphraseAlreadySet = summary.hasBackupPassphrase
                    draftBackupPassphrase = if (summary.hasBackupPassphrase) savedSecretPlaceholder else ""
                    testResultOk = true
                    testResultMessage = context.getString(R.string.remote_sync_github_config_imported, summary.repoLabel)
                }
                .onFailure { error ->
                    testResultOk = false
                    testResultMessage = error.message ?: context.getString(R.string.export_failed)
                }
        }
    }

    fun save() {
        if (!requireBackupPassphrase()) {
            return
        } else if (draftIsGitHub && parseGitHubRepoDraft() == null) {
            testResultOk = false
            testResultMessage = "Enter the repo as owner/name"
        } else {
            saveServerConfiguration()
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.remote_sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    ContextualHelpButton(
                        title = stringResource(R.string.remote_sync_title),
                        topics = listOf(
                            ContextualHelpTopic(
                                title = "Choose one provider",
                                body = "GitHub sync uses a private repository and commit history. SFTP and FTP/FTPS use your own server folder with rotated backup files."
                            ),
                            ContextualHelpTopic(
                                title = "Secrets stay local",
                                body = "Tokens, passwords, private-key passphrases, and backup encryption passphrases are stored encrypted on this device."
                            ),
                            ContextualHelpTopic(
                                title = "Test before saving",
                                body = "Use the connect/test action after changing credentials. For SFTP, trust the host key only when the fingerprint matches your server."
                            ),
                            ContextualHelpTopic(
                                title = "Recovery actions replace data",
                                body = "Restoring from a remote snapshot replaces local app data after creating a recovery backup, so use it when this device should match the remote copy."
                            )
                        )
                    )
                    IconButton(onClick = ::save) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        AdaptiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            RemoteConfigHeader(protocol = draftProtocol)
            RemoteProviderPicker(
                selectedProtocol = draftProtocol,
                onProtocolSelected = { newProtocol ->
                    // Only nudge the port if it's still sitting at the *other* protocol's
                    // default -- a custom port the user already typed must survive a
                    // protocol switch.
                    if (newProtocol == RemoteBackupProtocol.FTP && draftPort == "22") {
                        draftPort = "21"
                    } else if (newProtocol == RemoteBackupProtocol.SFTP && draftPort == "21") {
                        draftPort = "22"
                    }
                    draftProtocol = newProtocol
                }
            )
            AnimatedContent(
                targetState = draftIsGitHub,
                transitionSpec = { providerFieldsTransition() },
                label = "providerAccessFields"
            ) { isGitHub ->
            if (isGitHub) {
                RemoteConfigGroup(
                    title = stringResource(R.string.remote_sync_repository_access),
                    summary = stringResource(R.string.remote_sync_repository_access_summary),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_github)
                ) {
                    TextField(
                        value = draftGitHubToken,
                        onValueChange = { draftGitHubToken = it },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.remote_sync_token_label))
                                IconButton(
                                    onClick = { showGitHubPatHelpDialog = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = stringResource(R.string.cd_remote_sync_token_help),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        placeholder = {
                            if (githubTokenAlreadySet) Text(savedSecretPlaceholder)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    tokenExpiryStatus?.let { status ->
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.warning) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextField(
                        value = draftGitHubRepo,
                        onValueChange = { draftGitHubRepo = it },
                        label = { Text(stringResource(R.string.remote_sync_repo_label)) },
                        placeholder = { Text(stringResource(R.string.remote_sync_repo_placeholder)) },
                        singleLine = true,
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = draftGitHubBranch,
                        onValueChange = { draftGitHubBranch = it },
                        label = { Text(stringResource(R.string.remote_sync_branch_label)) },
                        singleLine = true,
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = draftGitHubApiBase,
                        onValueChange = { draftGitHubApiBase = it },
                        label = { Text(stringResource(R.string.remote_sync_api_base_label)) },
                        singleLine = true,
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.remote_sync_token_scope_hint),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    GitHubConfigTransferActions(
                        onExport = ::requestGitHubConfigExport,
                        onImport = ::chooseGitHubConfigImportFile,
                        enabled = !isTransferringGitHubConfig,
                        isBusy = isTransferringGitHubConfig
                    )
                    GitHubRecoveryActions(
                        onDownloadLatest = { showGitHubForceDownloadDialog = true },
                        enabled = !isDownloadingGitHubSnapshot && !isTestingConnection
                    )
                }
            } else {
                RemoteConfigGroup(
                    title = stringResource(R.string.remote_sync_server_location),
                    summary = if (draftIsFtp) {
                        stringResource(R.string.remote_sync_server_location_summary_ftp)
                    } else {
                        stringResource(R.string.remote_sync_server_location_summary_sftp)
                    },
                    icon = if (draftIsFtp) Icons.Default.Dns else Icons.Default.Storage
                ) {
                    TextField(
                        value = draftHost,
                        onValueChange = { draftHost = it },
                        label = { Text(stringResource(R.string.settings_sftp_host)) },
                        singleLine = true,
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TextField(
                            value = draftPort,
                            onValueChange = { new -> if (new.length <= 5 && new.all { it.isDigit() }) draftPort = new },
                            label = { Text(stringResource(R.string.settings_sftp_port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = YataCompactFieldShape,
                            colors = yataFieldColors(),
                            modifier = Modifier.weight(0.38f)
                        )
                        TextField(
                            value = draftUsername,
                            onValueChange = { draftUsername = it },
                            label = { Text(stringResource(R.string.settings_sftp_username)) },
                            singleLine = true,
                            shape = YataCompactFieldShape,
                            colors = yataFieldColors(),
                            modifier = Modifier.weight(0.62f)
                        )
                    }
                    TextField(
                        value = draftRemoteDir,
                        onValueChange = { draftRemoteDir = it },
                        label = { Text(stringResource(R.string.settings_sftp_remote_dir)) },
                        singleLine = true,
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            }
            AnimatedContent(
                targetState = draftProtocol,
                transitionSpec = { providerFieldsTransition() },
                label = "providerCredentialFields"
            ) { protocol ->
            if (protocol == RemoteBackupProtocol.FTP) {
                RemoteConfigGroup(
                    title = stringResource(R.string.remote_sync_credentials),
                    summary = stringResource(R.string.remote_sync_credentials_summary_encrypted),
                    icon = Icons.Default.Lock
                ) {
                    TextField(
                        value = draftPassword,
                        onValueChange = { draftPassword = it },
                        label = { Text(stringResource(R.string.settings_sftp_password)) },
                        placeholder = {
                            if (passwordAlreadySet) Text(savedSecretPlaceholder)
                        },
                        // A TextField's placeholder only renders while it's focused -- at rest the
                        // label alone sits in that space, so an already-saved password otherwise
                        // looks empty until tapped. supportingText has no such quirk.
                        supportingText = {
                            if (passwordAlreadySet) Text(stringResource(R.string.remote_sync_password_saved_hint))
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = YataCompactFieldShape,
                        colors = yataFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ftp_use_tls),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = draftFtpUseTls, onCheckedChange = { draftFtpUseTls = it })
                    }
                    AnimatedVisibility(
                        visible = !draftFtpUseTls,
                        enter = fadeIn(tween(YataDur.fade, easing = YataEase.emphDecel)) +
                            expandVertically(tween(YataDur.sheet, easing = YataEase.emphasized)),
                        exit = fadeOut(tween(YataDur.fade)) +
                            shrinkVertically(tween(YataDur.sheet, easing = YataEase.emphasized))
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.settings_ftp_plain_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = draftFtpUseTls,
                        enter = fadeIn(tween(YataDur.fade, easing = YataEase.emphDecel)) +
                            expandVertically(tween(YataDur.sheet, easing = YataEase.emphasized)),
                        exit = fadeOut(tween(YataDur.fade)) +
                            shrinkVertically(tween(YataDur.sheet, easing = YataEase.emphasized))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_ftp_strict_tls),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(checked = draftFtpStrictTls, onCheckedChange = { draftFtpStrictTls = it })
                            }
                            Text(
                                text = stringResource(R.string.settings_ftp_strict_tls_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (protocol != RemoteBackupProtocol.GITHUB) {
                RemoteConfigGroup(
                    title = stringResource(R.string.remote_sync_credentials),
                    summary = stringResource(R.string.remote_sync_credentials_summary_password_or_key),
                    icon = Icons.Default.Lock
                ) {
                    val authPasswordLabel = stringResource(R.string.settings_sftp_auth_password)
                    val authKeyLabel = stringResource(R.string.settings_sftp_auth_key)
                    SegmentedControl(
                        items = listOf("PASSWORD", "PRIVATE_KEY"),
                        selectedItem = draftAuthMethod,
                        onItemSelected = { draftAuthMethod = it },
                        labelProvider = { if (it == "PASSWORD") authPasswordLabel else authKeyLabel }
                    )
                    if (draftAuthMethod == "PRIVATE_KEY") {
                        TextField(
                            value = draftPrivateKey,
                            onValueChange = { draftPrivateKey = it },
                            label = { Text(stringResource(R.string.settings_sftp_private_key)) },
                            placeholder = { Text(stringResource(R.string.settings_sftp_private_key_placeholder), style = MaterialTheme.typography.bodySmall) },
                            minLines = 3,
                            maxLines = 6,
                            shape = YataFieldShape,
                            colors = yataFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = draftPassphrase,
                            onValueChange = { draftPassphrase = it },
                            label = { Text(stringResource(R.string.settings_sftp_passphrase)) },
                            placeholder = {
                                if (keyPassphraseAlreadySet) Text(savedSecretPlaceholder)
                            },
                            supportingText = {
                                if (keyPassphraseAlreadySet) Text(stringResource(R.string.remote_sync_passphrase_saved_hint))
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = YataCompactFieldShape,
                            colors = yataFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        TextField(
                            value = draftPassword,
                            onValueChange = { draftPassword = it },
                            label = { Text(stringResource(R.string.settings_sftp_password)) },
                            placeholder = {
                                if (passwordAlreadySet) Text(savedSecretPlaceholder)
                            },
                            supportingText = {
                                if (passwordAlreadySet) Text(stringResource(R.string.remote_sync_password_saved_hint))
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = YataCompactFieldShape,
                            colors = yataFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            }

            // Shared across all three transports -- GitHubSyncManager/SftpBackupManager/
            // FtpBackupManager all encrypt with the same stored passphrase before upload, so it
            // isn't provider-specific credentials and belongs outside the per-protocol groups.
            RemoteConfigGroup(
                title = stringResource(R.string.remote_sync_backup_encryption),
                summary = stringResource(R.string.remote_sync_backup_encryption_summary),
                icon = Icons.Default.Lock
            ) {
                TextField(
                    value = draftBackupPassphrase,
                    onValueChange = { draftBackupPassphrase = it },
                    label = { Text(stringResource(R.string.settings_backup_passphrase)) },
                    placeholder = {
                        if (backupPassphraseAlreadySet) Text(savedSecretPlaceholder)
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = YataCompactFieldShape,
                    colors = yataFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (backupPassphraseAlreadySet) {
                        stringResource(R.string.settings_backup_passphrase_set)
                    } else {
                        stringResource(R.string.settings_backup_passphrase_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = {
                    testResultOk = null
                    testResultMessage = null
                    pendingTrustFingerprint = null
                    isHostKeyMismatch = false
                    isTestingConnection = true
                    if (!requireBackupPassphrase()) {
                        return@FilledTonalButton
                    }
                    persistBackupPassphraseDraft()
                    if (draftIsGitHub) {
                        viewModel.connectGitHubConfiguration(
                            repoText = draftGitHubRepo,
                            token = enteredGitHubToken(),
                            apiBase = draftGitHubApiBase
                        ) { result ->
                            isTestingConnection = false
                            testResultOk = result.isSuccess
                            testResultMessage = if (result.isSuccess) {
                                "GitHub connected"
                            } else {
                                result.exceptionOrNull()?.message ?: context.getString(R.string.export_failed)
                            }
                        }
                    } else {
                        saveServerConfiguration {
                            if (draftIsFtp) {
                                viewModel.testFtpConnection { result ->
                                    isTestingConnection = false
                                    testResultOk = result.isSuccess
                                    testResultMessage = if (result.isSuccess) {
                                        context.getString(R.string.settings_sftp_connection_ok)
                                    } else {
                                        result.exceptionOrNull()?.message ?: context.getString(R.string.export_failed)
                                    }
                                }
                            } else {
                                viewModel.testSftpConnection { result ->
                                    isTestingConnection = false
                                    testResultOk = result.success
                                    val firstObservedKey = sftpHostKeyFingerprint == null &&
                                        result.fingerprint != null &&
                                        result.fingerprint.isNotBlank()
                                    if (firstObservedKey) {
                                        // The transport intentionally stopped before authentication.
                                        // Confirming below pins the key, then runs the real auth test.
                                        pendingTrustFingerprint = result.fingerprint
                                    } else if (result.success) {
                                        testResultMessage = context.getString(R.string.settings_sftp_connection_ok)
                                    } else {
                                        val mismatch = sftpHostKeyFingerprint != null &&
                                            result.fingerprint != null &&
                                            result.fingerprint != sftpHostKeyFingerprint
                                        if (mismatch) {
                                            isHostKeyMismatch = true
                                            pendingTrustFingerprint = result.fingerprint
                                        } else {
                                            testResultMessage = result.error?.message
                                                ?: context.getString(R.string.export_failed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = !isTestingConnection,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (draftIsGitHub) ImageVector.vectorResource(id = R.drawable.ic_github) else Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (draftIsGitHub) {
                        if (isTestingConnection) "Connecting..." else "Connect GitHub"
                    } else if (isTestingConnection) {
                        stringResource(R.string.settings_sftp_testing_connection)
                    } else {
                        stringResource(R.string.settings_sftp_test_connection)
                    }
                )
            }

            AnimatedVisibility(
                visible = pendingTrustFingerprint != null,
                enter = fadeIn(tween(YataDur.fade, easing = YataEase.emphDecel)) +
                    expandVertically(tween(YataDur.sheet, easing = YataEase.emphasized)),
                exit = fadeOut(tween(YataDur.fade)) +
                    shrinkVertically(tween(YataDur.sheet, easing = YataEase.emphasized))
            ) {
                val fingerprint = pendingTrustFingerprint.orEmpty()
                Surface(
                    color = if (isHostKeyMismatch) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(
                                if (isHostKeyMismatch) R.string.settings_sftp_host_key_changed else R.string.settings_sftp_trust_prompt,
                                fingerprint
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isHostKeyMismatch) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                pendingTrustFingerprint = null
                                isHostKeyMismatch = false
                                testResultMessage = null
                                isTestingConnection = true
                                viewModel.pinAndTestSftpConnection(fingerprint) { result ->
                                    isTestingConnection = false
                                    testResultOk = result.success
                                    if (result.success) {
                                        testResultMessage = context.getString(R.string.settings_sftp_connection_ok)
                                    } else {
                                        val changedAgain = result.fingerprint != null &&
                                            result.fingerprint != fingerprint
                                        if (changedAgain) {
                                            isHostKeyMismatch = true
                                            pendingTrustFingerprint = result.fingerprint
                                        } else {
                                            testResultMessage = result.error?.message
                                                ?: context.getString(R.string.export_failed)
                                        }
                                    }
                                }
                            },
                            colors = if (isHostKeyMismatch) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                        ) {
                            Text(
                                stringResource(
                                    if (isHostKeyMismatch) R.string.settings_sftp_trust_new_key else R.string.settings_sftp_trust_and_save
                                )
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = testResultMessage != null,
                enter = fadeIn(tween(YataDur.fade, easing = YataEase.emphDecel)) +
                    expandVertically(tween(YataDur.sheet, easing = YataEase.emphasized)),
                exit = fadeOut(tween(YataDur.fade)) +
                    shrinkVertically(tween(YataDur.sheet, easing = YataEase.emphasized))
            ) {
                val message = testResultMessage.orEmpty()
                Surface(
                    color = if (testResultOk == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (testResultOk == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (testResultOk == true) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (draftIsGitHub && githubConfigured) {
                RemoteConfigGroup(
                    title = stringResource(R.string.remote_sync_activity_title),
                    summary = stringResource(R.string.remote_sync_activity_summary),
                    icon = Icons.Default.History
                ) {
                    // Just the most recent entry here -- the full list is what SyncHistoryScreen
                    // is for. Embedding every entry inline used to make this already-long config
                    // screen scroll forever with no more information gained per row.
                    GitHubSyncActivitySummary(
                        latest = syncActivity.firstOrNull(),
                        isLoading = isLoadingSyncActivity,
                        error = syncActivityError,
                        thisDeviceLabels = thisDeviceLabels,
                        onViewAll = onNavigateToSyncHistory
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
        }
    }

    if (showGitHubPatHelpDialog) {
        AlertDialog(
            onDismissRequest = { showGitHubPatHelpDialog = false },
            title = { Text(stringResource(R.string.remote_sync_pat_help_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.remote_sync_pat_help_intro),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(stringResource(R.string.remote_sync_pat_help_step1))
                    Text(stringResource(R.string.remote_sync_pat_help_step2))
                    Text(stringResource(R.string.remote_sync_pat_help_step3))
                    Text(stringResource(R.string.remote_sync_pat_help_step4))
                    Text(stringResource(R.string.remote_sync_pat_help_step5))
                }
            },
            confirmButton = {
                TextButton(onClick = { showGitHubPatHelpDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    gitHubConfigTransferMode?.let { mode ->
        GitHubConfigPasswordDialog(
            mode = mode,
            onDismiss = { gitHubConfigTransferMode = null },
            onConfirm = { password ->
                gitHubConfigTransferMode = null
                when (mode) {
                    GitHubConfigTransferMode.EXPORT -> beginGitHubConfigExport(password)
                    GitHubConfigTransferMode.IMPORT -> importGitHubConfig(password)
                }
            }
        )
    }

    if (showGitHubForceDownloadDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDownloadingGitHubSnapshot) showGitHubForceDownloadDialog = false
            },
            title = { Text(stringResource(R.string.remote_sync_github_force_download_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.remote_sync_github_force_download_body))
                    if (isDownloadingGitHubSnapshot) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.remote_sync_github_force_download_progress),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDownloadingGitHubSnapshot,
                    onClick = {
                        if (parseGitHubRepoDraft() == null) {
                            testResultOk = false
                            testResultMessage = "Enter the repo as owner/name"
                            showGitHubForceDownloadDialog = false
                            return@TextButton
                        }
                        if (!requireBackupPassphrase()) {
                            showGitHubForceDownloadDialog = false
                            return@TextButton
                        }
                        testResultOk = null
                        testResultMessage = null
                        isDownloadingGitHubSnapshot = true
                        saveServerConfiguration {
                            viewModel.restoreLatestRemoteSnapshot { result ->
                                isDownloadingGitHubSnapshot = false
                                showGitHubForceDownloadDialog = false
                                testResultOk = result.isSuccess
                                testResultMessage = result.fold(
                                    onSuccess = { restorePoint ->
                                        context.getString(
                                            R.string.remote_sync_github_force_download_success,
                                            restorePoint.label
                                        )
                                    },
                                    onFailure = { error ->
                                        error.message ?: context.getString(R.string.export_failed)
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.remote_sync_github_force_download_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGitHubForceDownloadDialog = false },
                    enabled = !isDownloadingGitHubSnapshot
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private enum class GitHubConfigTransferMode {
    EXPORT,
    IMPORT
}

/** Only the most recent commit is fetched/shown here — the full history lives in
 * SyncHistoryScreen, one card per entry, with per-card lazy detail and restore. This row exists
 * to answer "did the last sync happen, and from where" at a glance without leaving the config
 * screen for it. */
@Composable
private fun GitHubSyncActivitySummary(
    latest: RestorePoint?,
    isLoading: Boolean,
    error: String?,
    thisDeviceLabels: Set<String>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            isLoading && latest == null -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            latest == null -> {
                Text(
                    text = stringResource(R.string.remote_sync_activity_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                val parsed = remember(latest.label) { SyncCommitMessage.parse(latest.label) }
                SyncActivityRow(
                    device = parsed.device,
                    summary = parsed.summary,
                    timestamp = latest.createdAt,
                    isThisDevice = parsed.device != null &&
                        thisDeviceLabels.any { it.equals(parsed.device, ignoreCase = true) }
                )
            }
        }
        TextButton(
            onClick = onViewAll,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.remote_sync_view_all_activity))
        }
    }
}

@Composable
private fun SyncActivityRow(
    device: String?,
    summary: String?,
    timestamp: java.time.Instant?,
    isThisDevice: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CloudUpload,
            contentDescription = null,
            tint = if (isThisDevice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = device ?: stringResource(R.string.remote_sync_activity_unknown_device),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (device == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (isThisDevice) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.remote_sync_activity_this_device),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            val details = listOfNotNull(summary, timestamp?.localized()).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GitHubRecoveryActions(
    onDownloadLatest: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.remote_sync_github_recovery_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedButton(
            onClick = onDownloadLatest,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.remote_sync_github_force_download_action))
        }
        Text(
            text = stringResource(R.string.remote_sync_github_force_download_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GitHubConfigTransferActions(
    onExport: () -> Unit,
    onImport: () -> Unit,
    enabled: Boolean,
    isBusy: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.remote_sync_github_config_transfer_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onExport, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.remote_sync_export_github_config))
            }
            OutlinedButton(onClick = onImport, enabled = enabled, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.remote_sync_import_github_config))
            }
        }
        AnimatedVisibility(visible = isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GitHubConfigPasswordDialog(
    mode: GitHubConfigTransferMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember(mode) { mutableStateOf("") }
    var confirmPassword by remember(mode) { mutableStateOf("") }
    val isExport = mode == GitHubConfigTransferMode.EXPORT
    val passwordsMismatch = isExport && confirmPassword.isNotEmpty() && password != confirmPassword
    val confirmEnabled = password.isNotBlank() && (!isExport || password == confirmPassword)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isExport) {
                        R.string.remote_sync_github_config_export_password_title
                    } else {
                        R.string.remote_sync_github_config_import_password_title
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.remote_sync_github_config_password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.remote_sync_github_config_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = YataCompactFieldShape,
                    colors = githubConfigPasswordFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (isExport) {
                    TextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text(stringResource(R.string.remote_sync_github_config_confirm_password_label)) },
                        singleLine = true,
                        isError = passwordsMismatch,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = YataCompactFieldShape,
                        colors = githubConfigPasswordFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordsMismatch) {
                        Text(
                            text = stringResource(R.string.remote_sync_github_config_password_mismatch),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = confirmEnabled) {
                Text(stringResource(if (isExport) R.string.action_export else R.string.action_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun githubConfigPasswordFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    errorIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun RemoteConfigHeader(
    protocol: RemoteBackupProtocol,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Crossfades the whole icon+title+body together on a provider switch, rather than each
        // Text popping to new content mid-frame -- the header is the one element on this screen
        // that changes identity (not just visibility) when the segmented control moves.
        AnimatedContent(
            targetState = protocol,
            transitionSpec = { providerFieldsTransition() },
            label = "remoteConfigHeader"
        ) { animatedProtocol ->
            val (title, body, icon) = when (animatedProtocol) {
                RemoteBackupProtocol.GITHUB -> Triple(
                    stringResource(R.string.remote_sync_github_title),
                    stringResource(R.string.remote_sync_github_body),
                    ImageVector.vectorResource(id = R.drawable.ic_github)
                )
                RemoteBackupProtocol.FTP -> Triple(
                    stringResource(R.string.remote_sync_ftp_title),
                    stringResource(R.string.remote_sync_ftp_body),
                    Icons.Default.Dns
                )
                RemoteBackupProtocol.SFTP -> Triple(
                    stringResource(R.string.remote_sync_sftp_title),
                    stringResource(R.string.remote_sync_sftp_body),
                    Icons.Default.Storage
                )
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(body, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Shared crossfade+rise used everywhere this screen swaps one provider's fields for another's --
 * a fast fade-out under a slightly slower fade/slide-in reads as the new content settling into
 * place rather than the two states crossing paths. */
private fun providerFieldsTransition() =
    (fadeIn(tween(YataDur.nav, easing = YataEase.emphDecel)) +
        slideInVertically(tween(YataDur.nav, easing = YataEase.emphDecel)) { it / 6 })
        .togetherWith(fadeOut(tween(YataDur.fade, easing = YataEase.emphAccel)))

@Composable
private fun RemoteProviderPicker(
    selectedProtocol: RemoteBackupProtocol,
    onProtocolSelected: (RemoteBackupProtocol) -> Unit,
    modifier: Modifier = Modifier
) {
    // Reuses the same sliding-pill SegmentedControl as the Password/Private-key choice below --
    // RemoteConfigHeader above already spells out the selected provider in full, so this only
    // needs to be a compact switch, not another set of icon cards repeating that description.
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.remote_sync_provider),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        // labelProvider isn't @Composable (SegmentedControl is generic over T and shared with the
        // Password/Private-key choice, which has no strings to resolve at all), so the labels are
        // resolved here and captured rather than called from inside the lambda.
        val githubLabel = stringResource(R.string.remote_sync_provider_github)
        val sftpLabel = stringResource(R.string.remote_sync_provider_sftp)
        val ftpLabel = stringResource(R.string.remote_sync_provider_ftp)
        SegmentedControl(
            items = listOf(RemoteBackupProtocol.GITHUB, RemoteBackupProtocol.SFTP, RemoteBackupProtocol.FTP),
            selectedItem = selectedProtocol,
            onItemSelected = onProtocolSelected,
            labelProvider = {
                when (it) {
                    RemoteBackupProtocol.GITHUB -> githubLabel
                    RemoteBackupProtocol.SFTP -> sftpLabel
                    RemoteBackupProtocol.FTP -> ftpLabel
                }
            }
        )
    }
}

@Composable
private fun RemoteConfigGroup(
    title: String,
    summary: String? = null,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            content()
        }
    }
}

private data class GitHubTokenExpiryStatus(
    val label: String,
    val warning: Boolean
)

private fun githubTokenExpiryStatus(epochMillis: Long?): GitHubTokenExpiryStatus? {
    if (epochMillis == null) return null
    val expiresAt = java.time.Instant.ofEpochMilli(epochMillis)
    val days = java.time.Duration.between(java.time.Instant.now(), expiresAt).toDays()
    val formatted = expiresAt.localized()
    return when {
        days < 0 -> GitHubTokenExpiryStatus("GitHub token expired on $formatted", warning = true)
        days <= 14 -> GitHubTokenExpiryStatus(
            "GitHub token expires in ${days.coerceAtLeast(0).formatDays()}: $formatted",
            warning = true
        )
        else -> GitHubTokenExpiryStatus("GitHub token expires on $formatted", warning = false)
    }
}

private fun Long.formatDays(): String =
    if (this == 1L) "1 day" else "$this days"

private const val MAX_GITHUB_CONFIG_TRANSFER_BYTES = 128 * 1024

private suspend fun writeTextToUri(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        output.write(text.toByteArray(Charsets.UTF_8))
        output.flush()
    } ?: throw IllegalStateException("Could not open export file")
}

private suspend fun readTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > MAX_GITHUB_CONFIG_TRANSFER_BYTES) {
                throw IllegalStateException(context.getString(R.string.remote_sync_github_config_import_too_large))
            }
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    } ?: throw IllegalStateException("Could not open import file")
}
