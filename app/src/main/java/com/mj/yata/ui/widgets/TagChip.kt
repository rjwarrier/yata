package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.ui.theme.LocalYataAccents

@Composable
fun TagChip(
    name: String,
    accentKey: String,
    modifier: Modifier = Modifier,
    size: String = "md", // "sm" | "md"
    onRemoveClick: (() -> Unit)? = null
) {
    val accents = LocalYataAccents.current
    val accentColor = if (accentKey == "error") {
        MaterialTheme.colorScheme.error
    } else {
        accents.getAccent(accentKey)
    }

    val paddingValues = if (size == "sm") {
        PaddingValues(horizontal = 8.dp, vertical = 2.dp)
    } else {
        PaddingValues(horizontal = 12.dp, vertical = 5.dp)
    }

    val fontSize = if (size == "sm") 10.sp else 12.sp
    val dotSize = if (size == "sm") 4.dp else 6.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9999.dp)) // Pill
            .background(accentColor.copy(alpha = 0.16f))
            .padding(paddingValues),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Tag dot
        Box(
            modifier = Modifier
                .size(dotSize)
                .background(accentColor, CircleShape)
        )
        // Tag label
        Text(
            text = name,
            color = accentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = fontSize
            )
        )
        // Optional remove button
        if (onRemoveClick != null) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.tag_chip_remove_tag),
                tint = accentColor,
                modifier = Modifier
                    .size(if (size == "sm") 11.dp else 13.dp)
                    .clickable { onRemoveClick() }
            )
        }
    }
}
