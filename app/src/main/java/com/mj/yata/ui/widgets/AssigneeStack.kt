package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Person
import com.mj.yata.ui.theme.LocalYataAccents

@Composable
fun PersonAvatar(
    initials: String,
    accentKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    drawRing: Boolean = false,
    ringColor: Color = MaterialTheme.colorScheme.surface
) {
    val accents = LocalYataAccents.current
    val bgColor = accents.getAccent(accentKey)
    val textColor = accents.onAccent

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (drawRing) Modifier.border(2.dp, ringColor, CircleShape) else Modifier
            )
            .background(bgColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.uppercase(),
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = when {
                    size >= 64.dp -> 20.sp
                    size >= 40.dp -> 14.sp
                    size >= 30.dp -> 11.sp
                    else -> 9.sp
                }
            )
        )
    }
}

@Composable
fun AssigneeStack(
    people: List<Person>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 24.dp,
    maxAvatars: Int = 3
) {
    val displayedPeople = people.take(maxAvatars)
    val remaining = people.size - maxAvatars

    Box(modifier = modifier) {
        displayedPeople.forEachIndexed { index, person ->
            PersonAvatar(
                initials = person.initials,
                accentKey = person.color,
                size = avatarSize,
                drawRing = index > 0,
                ringColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(start = (index * (avatarSize.value * 0.68f)).dp)
            )
        }
        if (remaining > 0) {
            val totalAvatars = displayedPeople.size
            Box(
                modifier = Modifier
                    .padding(start = (totalAvatars * (avatarSize.value * 0.68f)).dp)
                    .size(avatarSize)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$remaining",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (avatarSize >= 30.dp) 11.sp else 8.sp
                    )
                )
            }
        }
    }
}
