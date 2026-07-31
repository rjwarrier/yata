package com.mj.yata.ui.screen.main.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.domain.model.*
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.DragDropReorderableColumn
import com.mj.yata.ui.widgets.iconVectorFor
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.ProgressRing

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProjectsTab(
    projects: List<Project>,
    lists: List<YataList>,
    tasks: List<Task>,
    people: List<Person>,
    userName: String,
    userPhotoUri: String? = null,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    onNewProjectClick: () -> Unit,
    onToggleProjectStar: (String) -> Unit,
    onProjectsReordered: (List<Project>) -> Unit = {},
    onBulkArchiveProjects: (List<String>) -> Unit = {},
    peopleEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val activeProjects = remember(projects) { projects.activeProjects() }
    val archivedProjects = remember(projects) { projects.archivedProjects().sortedBy { it.sortOrder } }
    val sortedProjects = remember(activeProjects) { activeProjects.sortedBy { it.sortOrder } }
    val tasksByProject = remember(tasks) { tasks.groupBy { it.projectId } }
    var localOrder by remember { mutableStateOf(sortedProjects) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(sortedProjects) {
        if (!isDragging) localOrder = sortedProjects
    }

    val selectedIds = remember { mutableStateListOf<String>() }
    var selectModeOn by remember { mutableStateOf(false) }
    val selectionMode = selectModeOn
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top bar — swaps to a selection bar once projects are selected.
        if (selectionMode) {
            com.mj.yata.ui.widgets.TabSelectionTopBar(
                selectedCount = selectedIds.size,
                onCancel = { selectedIds.clear(); selectModeOn = false }
            ) {
                IconButton(onClick = { showBulkDeleteDialog = true }, enabled = selectedIds.isNotEmpty()) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.projects_archive_selected), tint = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            com.mj.yata.ui.widgets.TabTopBar(
                title = stringResource(R.string.tab_projects),
                onMenuClick = onMenuClick,
                userName = userName,
                userPhotoUri = userPhotoUri,
                onProfileClick = onProfileClick
            ) {
                com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = { selectModeOn = true }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.projects_select_projects)
                    )
                }
                com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search)
                    )
                }
            }
        }

        // 2. List of Projects — long-press drag to reorder.
        DragDropReorderableColumn(
            items = localOrder,
            key = { it.id },
            onMove = { from, to -> localOrder = localOrder.toMutableList().apply { add(to, removeAt(from)) } },
            onDragEnd = { onProjectsReordered(localOrder) },
            onDragStateChanged = { isDragging = it },
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 88.dp),
            headerItemCount = if (activeProjects.isEmpty()) 1 else 0,
            header = if (activeProjects.isEmpty()) {
                {
                    item {
                        com.mj.yata.ui.widgets.TabEmptyState(
                            icon = Icons.Default.Layers,
                            title = stringResource(R.string.projects_empty_title),
                            subtitle = stringResource(R.string.projects_empty_subtitle),
                            actionLabel = stringResource(R.string.projects_new_project),
                            onAction = onNewProjectClick
                        )
                    }
                }
            } else null,
            footer = {
                if (archivedProjects.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.projects_archived_header),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                        )
                    }
                    items(archivedProjects, key = { "archived_${it.id}" }) { archivedProject ->
                        val archivedProjectTasks = tasksByProject[archivedProject.id] ?: emptyList()
                        val archivedDoneTasks = archivedProjectTasks.count { it.done }
                        ProjectCard(
                            project = archivedProject,
                            totalTasks = archivedProjectTasks.size,
                            doneTasks = archivedDoneTasks,
                            progress = if (archivedProjectTasks.isNotEmpty()) archivedDoneTasks.toFloat() / archivedProjectTasks.size else 0f,
                            onClick = { onProjectClick(archivedProject.id) },
                            onToggleStar = { onToggleProjectStar(archivedProject.id) },
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
            },
            modifier = Modifier.weight(1f)
        ) { project ->
            val projectTasks = remember(tasksByProject, project.id) {
                tasksByProject[project.id] ?: emptyList()
            }

            val totalTasks = projectTasks.size
            val doneTasks = projectTasks.count { it.done }
            val progress = if (totalTasks > 0) doneTasks.toFloat() / totalTasks else 0f

            ProjectCard(
                project = project,
                totalTasks = totalTasks,
                doneTasks = doneTasks,
                progress = progress,
                selectionMode = selectionMode,
                selected = selectedIds.contains(project.id),
                onClick = {
                    if (selectionMode) {
                        if (selectedIds.contains(project.id)) selectedIds.remove(project.id) else selectedIds.add(project.id)
                    } else {
                        onProjectClick(project.id)
                    }
                },
                onToggleStar = { onToggleProjectStar(project.id) },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(pluralStringResource(R.plurals.confirm_archive_projects_title, selectedIds.size, selectedIds.size)) },
            text = { Text(stringResource(R.string.projects_tasks_inside_stay_linked_and_the_projects)) },
            confirmButton = {
                TextButton(onClick = {
                    onBulkArchiveProjects(selectedIds.toList())
                    selectedIds.clear()
                    selectModeOn = false
                    showBulkDeleteDialog = false
                }) {
                    Text(stringResource(R.string.archive_title), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
fun ProjectCard(
    project: Project,
    totalTasks: Int,
    doneTasks: Int,
    progress: Float,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accents = LocalYataAccents.current
    val projectColor = accents.getAccent(project.color)

    val iconVector = iconVectorFor(project.icon)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp), // xl corners
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectionMode) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_task_selected), tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    // Accent tile icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(projectColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = stringResource(R.string.projects_project_icon),
                            tint = projectColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$doneTasks/$totalTasks done",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.mj.yata.ui.widgets.StarToggleButton(
                        starred = project.starred,
                        onToggle = onToggleStar,
                        starredColor = accents.accentD,
                        starredContentDescription = "Unstar project",
                        unstarredContentDescription = "Star project"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    ProgressRing(
                        progress = progress,
                        size = 46.dp,
                        strokeWidth = 4.dp,
                        activeColor = projectColor
                    )
                }
            }
        }
    }
}
