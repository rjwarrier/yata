package com.mj.yata.ui.screen.settings

import android.content.Context
import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mj.yata.R
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.util.export.exportsDir
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAppScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by rememberSaveable { mutableStateOf(context.getString(R.string.share_app_default_message)) }
    var includeImage by rememberSaveable { mutableStateOf(true) }
    var includePlayStoreLink by rememberSaveable { mutableStateOf(true) }
    var includeGitHubLink by rememberSaveable { mutableStateOf(true) }
    var isPreparingShare by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chooserTitle = stringResource(R.string.settings_about_share)

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.share_app_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        AdaptiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (includeImage) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(R.drawable.share_promo),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                ShareOptionToggle(
                    label = stringResource(R.string.share_app_include_image),
                    description = stringResource(R.string.share_app_include_image_subtitle),
                    checked = includeImage,
                    onCheckedChange = { includeImage = it }
                )
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.share_app_message_label)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                ShareOptionToggle(
                    label = stringResource(R.string.settings_about_play_store),
                    description = YATA_PLAY_STORE_URL,
                    checked = includePlayStoreLink,
                    onCheckedChange = { includePlayStoreLink = it }
                )
                ShareOptionToggle(
                    label = stringResource(R.string.remote_sync_provider_github),
                    description = YATA_GITHUB_RELEASES_URL,
                    checked = includeGitHubLink,
                    onCheckedChange = { includeGitHubLink = it }
                )
            }

            Button(
                onClick = {
                    if (!isPreparingShare) {
                        val body = buildAppShareText(message, includePlayStoreLink, includeGitHubLink)
                        val attachImage = includeImage
                        isPreparingShare = true
                        scope.launch {
                            try {
                                shareApp(context, body, attachImage, chooserTitle)
                            } finally {
                                isPreparingShare = false
                            }
                        }
                    }
                },
                enabled = !isPreparingShare &&
                    (message.isNotBlank() || includePlayStoreLink || includeGitHubLink || includeImage),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.share_app_button, stringResource(R.string.app_name)))
            }
        }
        }
    }
}

@Composable
private fun ShareOptionToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private suspend fun shareApp(context: Context, body: String, includeImage: Boolean, chooserTitle: String) {
    val imageUri = if (includeImage) withContext(Dispatchers.IO) { stagePromoImage(context) } else null
    if (includeImage && imageUri == null) {
        Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        // image/jpeg (not text/plain) is what makes share targets treat this as an
        // image-with-caption rather than silently dropping the image.
        type = if (imageUri != null) "image/jpeg" else "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
        if (imageUri != null) {
            putExtra(Intent.EXTRA_STREAM, imageUri)
            clipData = ClipData.newRawUri(chooserTitle, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.export_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun stagePromoImage(context: Context): Uri? = runCatching {
    val imageFile = File(exportsDir(context), "yata_share.jpg")
    context.resources.openRawResource(R.drawable.share_promo).use { input ->
        imageFile.outputStream().use { output -> input.copyTo(output) }
    }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
}.getOrNull()
