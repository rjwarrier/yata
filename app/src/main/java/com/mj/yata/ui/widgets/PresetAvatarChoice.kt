package com.mj.yata.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.mj.yata.util.ProfilePhotoUtils

/** One inbuilt-icon option in the profile avatar picker — used by both Settings' profile editor
 * and the welcome-screen profile setup step. Renders [ProfilePhotoUtils.presetAvatarBitmap] as a
 * tinted glyph on a circular chip; [onClick] is left to the caller to decide what "picking" this
 * preset actually does (Settings routes it through the crop confirm flow the same as a photo). */
@Composable
fun PresetAvatarChoice(
    preset: ProfilePhotoUtils.PresetAvatar,
    label: String,
    context: android.content.Context,
    onClick: () -> Unit
) {
    val imageBitmap = remember(context, preset) {
        ProfilePhotoUtils.presetAvatarBitmap(context, preset).asImageBitmap()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
