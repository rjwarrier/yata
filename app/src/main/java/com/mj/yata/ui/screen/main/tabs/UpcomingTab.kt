package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.TaskRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun UpcomingTab(
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
    val today = remember { LocalDate.now() }
    
    // Generate 7 days starting from today
    val days = remember(today) {
        List(7) { today.plusDays(it.toLong()) }
    }

    var selectedDay by remember { mutableStateOf(today) }

    val monthLabel = remember(selectedDay) {
        selectedDay.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(Locale.getDefault())
    }

    val selectedDayTasks = remember(tasks, selectedDay) {
        val dateStr = selectedDay.toString()
        tasks.filter { it.due == dateStr }
    }

    val selectedDayLabel = remember(selectedDay) {
        selectedDay.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
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
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
            )
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

        Spacer(modifier = Modifier.height(8.dp))

        // 2. 7-Day Agenda Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { day ->
                val isSelected = day == selectedDay
                val hasTasks = tasks.any { it.due == day.toString() }
                
                val dayName = day.format(DateTimeFormatter.ofPattern("E")).uppercase()
                val dayOfMonth = day.dayOfMonth.toString()

                val pillColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
                    label = "dayPillColor"
                )
                val onPillColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
                    label = "dayPillLabelColor"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(pillColor)
                        .clickable { selectedDay = day }
                        .padding(vertical = 10.dp, horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dayName,
                        color = onPillColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = dayOfMonth,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                    
                    // Dot indicating tasks
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                color = when {
                                    isSelected && hasTasks -> MaterialTheme.colorScheme.onPrimary
                                    hasTasks -> MaterialTheme.colorScheme.primary
                                    else -> Color.Transparent
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Agenda Header
        Text(
            text = selectedDayLabel,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // 4. Task list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            if (selectedDayTasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks scheduled.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                items(selectedDayTasks, key = { it.id }) { task ->
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
