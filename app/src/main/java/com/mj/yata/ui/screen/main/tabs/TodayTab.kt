package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.ProgressRing
import com.mj.yata.ui.widgets.TaskRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TodayTab(
    tasks: List<Task>,
    lists: List<YataList>,
    projects: List<Project>,
    people: List<Person>,
    tags: List<Tag>,
    userName: String,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onToggleDone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayStr = remember { LocalDate.now().toString() }
    val todayDateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d")).uppercase()
    }

    // Filter tasks due today (or overdue)
    val todayTasks = remember(tasks, todayStr) {
        tasks.filter { it.due != null && it.due <= todayStr }
    }

    // State for filter chips
    var selectedFilter by remember { mutableStateOf("All") } // "All" | "Assigned to me" | "High Priority"

    val myId = remember(people) { people.find { it.isMe }?.id ?: "me" }

    val filteredTasks = remember(todayTasks, selectedFilter, myId) {
        when (selectedFilter) {
            "Assigned to me" -> todayTasks.filter { it.assigneeIds.contains(myId) }
            "High Priority" -> todayTasks.filter { it.priority == "high" }
            else -> todayTasks
        }
    }

    val doneCount = filteredTasks.count { it.done }
    val totalCount = filteredTasks.size
    val progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f
    val remainingCount = totalCount - doneCount

    val groupedTasks = remember(filteredTasks) {
        filteredTasks.groupBy { it.section ?: "Afternoon" }
    }
    
    val sortedSections = remember(groupedTasks) {
        groupedTasks.keys.sortedWith { s1, s2 ->
            val order = listOf("Morning", "Afternoon")
            val i1 = order.indexOf(s1)
            val i2 = order.indexOf(s2)
            when {
                i1 != -1 && i2 != -1 -> i1.compareTo(i2)
                i1 != -1 -> -1
                i2 != -1 -> 1
                else -> s1.compareTo(s2)
            }
        }
    }

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
                        contentDescription = "Search tasks",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Profile avatar triggers Settings
                Box(modifier = Modifier.clickable { onProfileClick() }) {
                    PersonAvatar(
                        initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                        accentKey = "accentC",
                        size = 32.dp
                    )
                }
            }
        }

        // 2. Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todayDateLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (remainingCount == 0 && totalCount > 0) "All caught up!" else "$remainingCount to go",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            ProgressRing(
                progress = progress,
                size = 56.dp,
                strokeWidth = 5.dp
            )
        }

        // 3. Scrollable filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("All", "Assigned to me", "High Priority").forEach { filter ->
                val isSelected = filter == selectedFilter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = { Text(text = filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Task sections
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 88.dp)
        ) {
            if (filteredTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks for today.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                sortedSections.forEach { sectionName ->
                    val sectionTasks = groupedTasks[sectionName] ?: emptyList()
                    if (sectionTasks.isNotEmpty()) {
                        item {
                            Text(
                                text = sectionName.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                        items(sectionTasks, key = { it.id }) { task ->
                            val taskList = lists.find { it.id == task.listId }
                            val taskAssignees = task.assigneeIds.mapNotNull { pid -> people.find { it.id == pid } }
                            val taskTags = task.effectiveTags(lists, projects, tags)
                            
                            TaskRow(
                                task = task,
                                list = taskList,
                                assignees = taskAssignees,
                                tags = taskTags,
                                onToggleDone = { onToggleDone(task.id) },
                                onTaskClick = { onTaskClick(task.id) },
                                modifier = Modifier.animateItemPlacement(tween(YataDur.sheet, easing = YataEase.emphasized))
                            )
                        }
                    }
                }
            }
        }
    }
}
