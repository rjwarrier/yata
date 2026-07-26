package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.domain.model.*
import com.mj.yata.util.sortedByEntityMode
import com.mj.yata.ui.sheets.GroupAssignSheet
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.ProgressRing

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PeopleTab(
    people: List<Person>,
    personGroups: List<PersonGroup>,
    tasks: List<Task>,
    userName: String,
    userPhotoUri: String? = null,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onPersonClick: (String) -> Unit,
    onAddPersonClick: () -> Unit,
    onAssignGroup: (personIds: List<String>, groupId: String) -> Unit,
    onCreateGroupAndAssign: (id: String, name: String, personIds: List<String>) -> Unit,
    onToggleStar: (String) -> Unit = {},
    onDeleteGroup: (PersonGroup) -> Unit = {},
    sortMode: com.mj.yata.util.EntitySortMode = com.mj.yata.util.EntitySortMode.NAME_ASC,
    onSortModeChange: (com.mj.yata.util.EntitySortMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tasksByPerson = remember(tasks) {
        val map = mutableMapOf<String, MutableList<Task>>()
        tasks.forEach { task ->
            task.assigneeIds.forEach { pid ->
                map.getOrPut(pid) { mutableListOf() }.add(task)
            }
        }
        map
    }
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectModeOn by remember { mutableStateOf(false) }
    val selectionMode = selectModeOn
    var showGroupPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top bar — swaps to a selection bar once people are selected.
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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_bulk_cancel_selection), tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                TextButton(onClick = { showGroupPicker = true }) {
                    Text(stringResource(R.string.people_add_to_group))
                }
            }
        } else {
            // Title lives in the same row as the icons instead of a separate stacked
            // header, so this tab doesn't burn an extra ~50dp of vertical space.
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
                        contentDescription = stringResource(R.string.cd_open_menu),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "People",
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
                    com.mj.yata.ui.widgets.EntitySortMenuButton(
                        current = sortMode,
                        onSelect = onSortModeChange,
                        contentDescription = stringResource(R.string.people_sort_people)
                    )
                    IconButton(onClick = { selectModeOn = true }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.people_select_people),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search),
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

        // 3. Scrollable, grouped, multi-selectable list of people
        val activePeople = remember(people) { people.activePeople() }
        val archivedPeople = remember(people) { people.archivedPeople() }
        val groupedIds = personGroups.map { it.id }.toSet()
        fun List<Person>.sorted() = sortedByEntityMode(
            sortMode,
            name = { it.name },
            starred = { it.starred },
            taskCount = { tasksByPerson[it.id]?.size ?: 0 }
        )
        val ungrouped = activePeople.filter { it.groupId == null || it.groupId !in groupedIds }.sorted()
        val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (people.isEmpty()) {
                item {
                    com.mj.yata.ui.widgets.TabEmptyState(
                        icon = Icons.Default.Groups,
                        title = "No people yet",
                        subtitle = "Add people to assign tasks and track who's doing what.",
                        actionLabel = "Add person",
                        onAction = onAddPersonClick
                    )
                }
            }
            personGroups.forEach { group ->
                val groupPeople = activePeople.filter { it.groupId == group.id }.sorted()
                if (groupPeople.isNotEmpty()) {
                    val expanded = expandedGroups[group.id] ?: true
                    item(key = "header_${group.id}") {
                        GroupHeader(
                            title = group.name,
                            expanded = expanded,
                            onToggle = { expandedGroups[group.id] = !expanded },
                            onDelete = { onDeleteGroup(group) }
                        )
                    }
                    if (expanded) {
                        items(groupPeople, key = { "person_${it.id}" }) { person ->
                            PersonListRow(
                                person = person,
                                tasksByPerson = tasksByPerson,
                                selectionMode = selectionMode,
                                selectedIds = selectedIds,
                                onPersonClick = onPersonClick,
                                onToggleStar = onToggleStar
                            )
                        }
                    }
                }
            }

            if (ungrouped.isNotEmpty()) {
                val ungroupedExpanded = expandedGroups["ungrouped"] ?: true
                if (personGroups.isNotEmpty()) {
                    item(key = "header_ungrouped") {
                        GroupHeader(
                            title = "Ungrouped",
                            expanded = ungroupedExpanded,
                            onToggle = { expandedGroups["ungrouped"] = !ungroupedExpanded }
                        )
                    }
                }
                if (ungroupedExpanded) {
                    items(ungrouped, key = { "person_${it.id}" }) { person ->
                        PersonListRow(
                            person = person,
                            tasksByPerson = tasksByPerson,
                            selectionMode = selectionMode,
                            selectedIds = selectedIds,
                            onPersonClick = onPersonClick,
                            onToggleStar = onToggleStar
                        )
                    }
                }
            }

            if (archivedPeople.isNotEmpty()) {
                val archivedExpanded = expandedGroups["archived"] ?: false
                item(key = "header_archived") {
                    GroupHeader(
                        title = "Archived",
                        expanded = archivedExpanded,
                        onToggle = { expandedGroups["archived"] = !archivedExpanded }
                    )
                }
                if (archivedExpanded) {
                    items(archivedPeople.sorted(), key = { "archived_${it.id}" }) { person ->
                        PersonRow(
                            person = person,
                            totalTasks = 0,
                            doneTasks = 0,
                            progress = 0f,
                            onClick = { onPersonClick(person.id) },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                AddPersonDashedRow(onClick = onAddPersonClick)
            }
        }
    }

    if (showGroupPicker) {
        ModalBottomSheet(
            onDismissRequest = { showGroupPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            GroupAssignSheet(
                title = "Add ${selectedIds.size} people to group",
                groups = personGroups,
                groupId = { it.id },
                groupName = { it.name },
                groupColorKey = { it.color },
                onSelectGroup = { groupId ->
                    onAssignGroup(selectedIds.toList(), groupId)
                    selectedIds.clear()
                    showGroupPicker = false
                },
                onCreateGroup = { id, name ->
                    onCreateGroupAndAssign(id, name, selectedIds.toList())
                    selectedIds.clear()
                    showGroupPicker = false
                },
                onDismiss = { showGroupPicker = false }
            )
        }
    }
}

/** One person's row within a group's (or "Ungrouped") alphabetically-sorted list. */
@Composable
private fun PersonListRow(
    person: Person,
    tasksByPerson: Map<String, List<Task>>,
    selectionMode: Boolean,
    selectedIds: MutableList<String>,
    onPersonClick: (String) -> Unit,
    onToggleStar: (String) -> Unit
) {
    val personTasks = remember(tasksByPerson, person.id) {
        tasksByPerson[person.id] ?: emptyList()
    }
    val doneCount = personTasks.count { it.done }
    val overdueCount = remember(personTasks) { com.mj.yata.util.AnalyticsUtils.overdueCount(personTasks) }
    PersonRow(
        person = person,
        totalTasks = personTasks.size,
        doneTasks = doneCount,
        overdueTasks = overdueCount,
        progress = if (personTasks.isNotEmpty()) doneCount.toFloat() / personTasks.size else 0f,
        selectionMode = selectionMode,
        selected = selectedIds.contains(person.id),
        onClick = {
            if (selectionMode) {
                if (selectedIds.contains(person.id)) selectedIds.remove(person.id) else selectedIds.add(person.id)
            } else {
                onPersonClick(person.id)
            }
        },
        onToggleStar = { onToggleStar(person.id) },
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonRow(
    person: Person,
    totalTasks: Int,
    doneTasks: Int,
    progress: Float,
    onClick: () -> Unit,
    overdueTasks: Int = 0,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleStar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accents = LocalYataAccents.current
    val accentColor = accents.getAccent(person.color)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
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
                Spacer(modifier = Modifier.width(14.dp))
            }

            val openTasks = totalTasks - doneTasks
            BadgedBox(
                badge = {
                    if (openTasks > 0) {
                        Badge { Text(if (openTasks > 99) "99+" else openTasks.toString()) }
                    }
                }
            ) {
                PersonAvatar(
                    initials = person.initials,
                    accentKey = person.color,
                    photoUri = person.photoUri,
                    size = 44.dp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    if (person.isMe) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "YOU",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "$totalTasks assigned · $doneTasks done",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                )
                if (overdueTasks > 0) {
                    Text(
                        text = "$overdueTasks overdue",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!selectionMode) {
                com.mj.yata.ui.widgets.StarToggleButton(
                    starred = person.starred,
                    onToggle = onToggleStar,
                    starredColor = accents.accentD,
                    starredContentDescription = "Unstar person",
                    unstarredContentDescription = "Star person"
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Progress indicator
                ProgressRing(
                    progress = progress,
                    size = 32.dp,
                    strokeWidth = 3.dp,
                    activeColor = accentColor
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.people_details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
        label = "groupChevron"
    )
    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse group" else "Expand group",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(rotation)
            )
        }
        if (onDelete != null) {
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_delete_group),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (showDeleteDialog && onDelete != null) {
        com.mj.yata.ui.widgets.GroupDeleteConfirmDialog(
            groupTitle = title,
            entityLabel = "Members",
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
fun AddPersonDashedRow(
    onClick: () -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                drawRoundRect(
                    color = outlineColor,
                    style = stroke,
                    cornerRadius = CornerRadius(12.dp.toPx())
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
                contentDescription = stringResource(R.string.people_add_person),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Add person",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
