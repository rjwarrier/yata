package com.mj.yata.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mj.yata.R
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.util.export.exportsDir
import java.io.File

private const val SHARE_LINK = "https://github.com/rjwarrier/yata/releases"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAppScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var message by rememberSaveable { mutableStateOf(context.getString(R.string.share_app_default_message)) }
    var includeImage by rememberSaveable { mutableStateOf(true) }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.share_app_include_image),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = stringResource(R.string.share_app_include_image_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = includeImage, onCheckedChange = { includeImage = it })
                }
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.share_app_message_label)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.share_app_link_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = SHARE_LINK,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { shareApp(context, message, includeImage, chooserTitle) },
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

private fun shareApp(context: Context, message: String, includeImage: Boolean, chooserTitle: String) {
    val body = buildString {
        val trimmed = message.trim()
        if (trimmed.isNotEmpty()) {
            append(trimmed)
            append("\n\n")
        }
        append(SHARE_LINK)
    }
    val imageUri = if (includeImage) stagePromoImage(context) else null
    val intent = Intent(Intent.ACTION_SEND).apply {
        // image/png (not text/plain) is what makes share targets treat this as an
        // image-with-caption rather than silently dropping the image.
        type = if (imageUri != null) "image/png" else "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
        if (imageUri != null) {
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun stagePromoImage(context: Context): Uri? = runCatching {
    val imageFile = File(exportsDir(context), "yata_share.png")
    context.resources.openRawResource(R.drawable.share_promo).use { input ->
        imageFile.outputStream().use { output -> input.copyTo(output) }
    }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
}.getOrNull()
