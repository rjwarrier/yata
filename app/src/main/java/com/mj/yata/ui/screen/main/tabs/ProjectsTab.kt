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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.widgets.DragDropReorderableColumn
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedIds.clear(); selectModeOn = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                IconButton(onClick = { showBulkDeleteDialog = true }, enabled = selectedIds.isNotEmpty()) {
                    Icon(Icons.Default.Delete, contentDescription = "Archive selected", tint = MaterialTheme.colorScheme.error)
                }
            }
        } else {
        // Title lives in the same row as the icons instead of a separate
        // stacked header, so this tab doesn't burn an extra ~50dp of vertical space.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open drawer",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Projects",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { selectModeOn = true }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Select projects",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(modifier = Modifier.clickable { onProfileClick() }) {
                    PersonAvatar(
                        initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                        accentKey = "accentC",
                        size = 32.dp,
                        photoUri = userPhotoUri
                    )
                }
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
                            title = "No projects yet",
                            subtitle = "Group related tasks into a project to track progress together."
                        )
                    }
                }
            } else null,
            footer = {
                item { NewProjectDashedCard(onClick = onNewProjectClick) }
                if (archivedProjects.isNotEmpty()) {
                    item {
                        Text(
                            text = "ARCHIVED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                        )
                    }
                    items(archivedProjects, key = { "archived_${it.id}" }) { archivedProject ->
                        val archivedProjectTasks = tasks.filter { it.projectId == archivedProject.id }
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
            val projectTasks = remember(tasks, project.id) {
                tasks.filter { it.projectId == project.id }
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
            title = { Text("Archive ${selectedIds.size} project(s)?") },
            text = { Text("Tasks inside stay linked, and the projects are hidden from active project surfaces until you restore them.") },
            confirmButton = {
                TextButton(onClick = {
                    onBulkArchiveProjects(selectedIds.toList())
                    selectedIds.clear()
                    selectModeOn = false
                    showBulkDeleteDialog = false
                }) {
                    Text("Archive", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
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

    val iconVector = when (project.icon) {
        "home" -> Icons.Default.Home
        "star" -> Icons.Default.Star
        else -> Icons.Default.Layers
    }

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
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
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
                            contentDescription = "Project icon",
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

@Composable
fun NewProjectDashedCard(
    onClick: () -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                )
                drawRoundRect(
                    color = outlineColor,
                    style = stroke,
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add project",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "New project",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
