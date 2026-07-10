package com.mj.yata.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activeProjects
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.YataSelectChip

/**
 * Chip-strip picker surfaced when a dragged task is held over the top/bottom edge of a
 * list/project detail screen — the drop target for a cross-container move.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskMoveToPickerSheet(
    lists: List<YataList>,
    projects: List<Project>,
    onSelectList: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = LocalYataAccents.current
    val activeProjects = projects.activeProjects()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Move task to...",
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
        )

        if (lists.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "LISTS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lists.forEach { list ->
                        val color = accents.getAccent(list.color)
                        YataSelectChip(
                            label = list.name,
                            selected = false,
                            showCheck = false,
                            onClick = { onSelectList(list.id) },
                            tint = color,
                            dotColor = color
                        )
                    }
                }
            }
        }

        if (activeProjects.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROJECTS",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeProjects.forEach { pr ->
                        val color = accents.getAccent(pr.color)
                        YataSelectChip(
                            label = pr.name,
                            selected = false,
                            showCheck = false,
                            onClick = { onSelectProject(pr.id) },
                            tint = color,
                            dotColor = color
                        )
                    }
                }
            }
        }
    }
}
