package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
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
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.AssigneeStack
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
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    onNewProjectClick: () -> Unit,
    onToggleProjectStar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                        size = 32.dp
                    )
                }
            }
        }

        // 2. Title Header
        Text(
            text = "Projects",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        // 3. Grid of Projects
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(projects, key = { it.id }) { project ->
                val projectLists = remember(lists, project.id) {
                    lists.filter { it.projectId == project.id }
                }
                val listIds = projectLists.map { it.id }
                val projectTasks = remember(tasks, listIds) {
                    tasks.filter { listIds.contains(it.listId) }
                }

                val totalTasks = projectTasks.size
                val doneTasks = projectTasks.count { it.done }
                val progress = if (totalTasks > 0) doneTasks.toFloat() / totalTasks else 0f

                val projectPeople = remember(projectTasks, people) {
                    val pids = projectTasks.flatMap { it.assigneeIds }.toSet()
                    people.filter { pids.contains(it.id) }
                }

                ProjectCard(
                    project = project,
                    listCount = projectLists.size,
                    totalTasks = totalTasks,
                    doneTasks = doneTasks,
                    progress = progress,
                    lists = projectLists,
                    members = projectPeople,
                    onClick = { onProjectClick(project.id) },
                    onToggleStar = { onToggleProjectStar(project.id) },
                    modifier = Modifier.animateItemPlacement(tween(YataDur.sheet, easing = YataEase.emphasized))
                )
            }

            item {
                NewProjectDashedCard(onClick = onNewProjectClick)
            }
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    listCount: Int,
    totalTasks: Int,
    doneTasks: Int,
    progress: Float,
    lists: List<YataList>,
    members: List<Person>,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
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
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$listCount lists · $doneTasks/$totalTasks done",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val starScale = remember { Animatable(1f) }
                    LaunchedEffect(project.starred) {
                        if (project.starred) {
                            starScale.snapTo(0.4f)
                            starScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                        }
                    }
                    IconButton(onClick = onToggleStar, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (project.starred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (project.starred) "Unstar project" else "Star project",
                            tint = if (project.starred) accents.accentD else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(20.dp)
                                .scale(starScale.value)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    ProgressRing(
                        progress = progress,
                        size = 46.dp,
                        strokeWidth = 4.dp,
                        activeColor = projectColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Footer: list swatches + assignee stack
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // List swatches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    lists.take(6).forEach { list ->
                        val swatchColor = accents.getAccent(list.color)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(swatchColor, CircleShape)
                        )
                    }
                }

                // Member avatars
                if (members.isNotEmpty()) {
                    AssigneeStack(
                        people = members,
                        avatarSize = 24.dp
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
