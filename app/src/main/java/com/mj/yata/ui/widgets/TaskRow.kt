package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.Task
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.theme.LocalYataAccents

@Composable
fun TaskRow(
    task: Task,
    list: YataList?,
    assignees: List<Person>,
    tags: List<Tag>,
    onToggleDone: () -> Unit,
    onTaskClick: () -> Unit,
    modifier: Modifier = Modifier,
    showList: Boolean = true
) {
    val accents = LocalYataAccents.current
    val listColor = list?.let { accents.getAccent(it.color) } ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onTaskClick() }
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left round checkbox
        SpringyCheck(
            checked = task.done,
            onCheckedChange = { onToggleDone() },
            color = listColor,
            size = 24.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Middle area
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = task.title,
                    color = if (task.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (task.flag) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Flagged",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                PriorityBars(priority = task.priority)
            }

            // Meta row below
            if (task.time != null || (showList && list != null) || task.recurrence != null || tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    task.time?.let { time ->
                        Text(
                            text = time,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                    }

                    if (showList && list != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(listColor, CircleShape)
                            )
                            Text(
                                text = list.name,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    task.recurrence?.let {
                        RecurrenceBadge(recurrence = it, compact = true)
                    }

                    tags.take(2).forEach { t ->
                        TagChip(name = t.name, accentKey = t.color, size = "sm")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right assignee stack
        if (assignees.isNotEmpty()) {
            AssigneeStack(
                people = assignees,
                avatarSize = 24.dp
            )
        }
    }
}
