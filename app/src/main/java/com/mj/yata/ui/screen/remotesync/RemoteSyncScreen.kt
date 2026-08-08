package com.mj.yata.ui.screen.remotesync

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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dns
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
import com.mj.yata.domain.model.RemoteBackupProtocol
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.SegmentedControl
import com.mj.yata.ui.widgets.YataCompactFieldShape
import com.mj.yata.ui.widgets.YataFieldShape
import com.mj.yata.ui.widgets.yataFieldColors
import com.mj.yata.util.localized

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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val remoteBackupProtocol = uiState.remoteBackupProtocol
    val sftpHost = uiState.sftpHost
    val sftpPort = uiState.sftpPort
    val sftpUsername = uiState.sftpUsername
    val sftpAuthMethod = uiState.sftpAuthMethod
    val sftpRemoteDir = uiState.sftpRemoteDir
    val sftpHostKeyFingerprint = uiState.sftpHostKeyFingerprint
    val ftpUseTls = uiState.ftpUseTls
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
    var draftGitHubRepo by remember { mutableStateOf(listOf(githubOwner, githubRepo).filter { it.isNotBlank() }.joinToString("/")) }
    var draftGitHubBranch by remember { mutableStateOf(githubBranch.ifBlank { "main" }) }
    var draftGitHubApiBase by remember { mutableStateOf(githubApiBase) }
    val passwordAlreadySet = remember { viewModel.hasRemoteBackupPassword() }
    val githubTokenAlreadySet = remember { viewModel.hasGitHubToken() }
    val keyPassphraseAlreadySet = remember { viewModel.hasSftpKeyPassphrase() }
    val backupPassphraseAlreadySet = remember { viewModel.hasRemoteBackupPassphrase() }
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
    val tokenExpiryStatus = githubTokenExpiryStatus(githubTokenExpiresAt)
    fun enteredGitHubToken(): String =
        draftGitHubToken.takeUnless { githubTokenAlreadySet && it == savedSecretPlaceholder }.orEmpty()
    fun enteredPassword(): String =
        draftPassword.takeUnless { passwordAlreadySet && it == savedSecretPlaceholder }.orEmpty()
    fun enteredKeyPassphrase(): String =
        draftPassphrase.takeUnless { keyPassphraseAlreadySet && it == savedSecretPlaceholder }.orEmpty()
    fun enteredBackupPassphrase(): String =
        draftBackupPassphrase.takeUnless { backupPassphraseAlreadySet && it == savedSecretPlaceholder }.orEmpty()

    fun parseGitHubRepoDraft(): Pair<String, String>? {
        val parts = draftGitHubRepo.trim().split("/", limit = 2)
        return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            parts[0] to parts[1]
        } else {
            null
        }
    }

    fun saveServerConfiguration(onSaved: () -> Unit = {}) {
        // Blank means "keep whatever is stored" rather than "remove encryption" -- silently
        // dropping to unencrypted uploads because a field was left empty is not a default anyone
        // would want. Shared across all three transports, so this runs before the per-provider
        // branches below (one of which returns early for GitHub).
        enteredBackupPassphrase().takeIf { it.isNotBlank() }?.let(viewModel::setRemoteBackupPassphrase)
        if (draftIsGitHub) {
            enteredGitHubToken().takeIf { it.isNotBlank() }?.let(viewModel::setGitHubToken)
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
            host = draftHost,
            port = draftPort.toIntOrNull() ?: sftpPort,
            username = draftUsername,
            remoteDir = draftRemoteDir,
            authMethod = draftAuthMethod,
            onSaved = onSaved
        )
    }

    fun save() {
        if (draftIsGitHub && parseGitHubRepoDraft() == null) {
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
                    IconButton(onClick = ::save) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                    modifier = Modifier.size(28.dp)
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
            Spacer(modifier = Modifier.height(12.dp))
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
}

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
