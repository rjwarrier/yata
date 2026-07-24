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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.mj.yata.domain.model.*
import com.mj.yata.util.sortedByEntityMode
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar

@Composable
fun TagsTab(
    tags: List<Tag>,
    tagGroups: List<TagGroup>,
    tasks: List<Task>,
    lists: List<YataList>,
    projects: List<Project>,
    userName: String,
    userPhotoUri: String? = null,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onNewTagClick: () -> Unit,
    onToggleStar: (String) -> Unit = {},
    onDeleteGroup: (TagGroup) -> Unit = {},
    onBulkDeleteTags: (List<String>) -> Unit = {},
    tagsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectModeOn by remember { mutableStateOf(false) }
    val selectionMode = selectModeOn
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(com.mj.yata.util.EntitySortMode.NAME_ASC) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top bar — swaps to a selection bar once tags are selected.
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
                    Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
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
                    contentDescription = "Open menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Tags",
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
                    onSelect = { sortMode = it },
                    contentDescription = "Sort tags"
                )
                IconButton(onClick = { selectModeOn = true }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Select tags",
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

        // 3. Scrollable, grouped tag cloud
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
            val groupedIds = tagGroups.map { it.id }.toSet()
            // Defense-in-depth: this tab is never routed to while tags are disabled, but if it
            // ever were, this keeps it from computing/showing tag-task associations anyway —
            // matching the pattern TodayTab/UpcomingTab already use for their cross-feature reads.
            // (total, done) per tag — used for both the "N open" label and the progress ring,
            // matching PeopleTab's PersonRow (totalTasks/doneTasks/progress) convention.
            val tagTaskCounts = remember(tasks, projects, tagsEnabled) {
                if (!tagsEnabled) return@remember emptyMap()
                val totals = mutableMapOf<String, Int>()
                val done = mutableMapOf<String, Int>()
                tasks.forEach { task ->
                    task.effectiveTagIds(projects).forEach { tagId ->
                        totals[tagId] = (totals[tagId] ?: 0) + 1
                        if (task.done) done[tagId] = (done[tagId] ?: 0) + 1
                    }
                }
                totals.mapValues { (tagId, total) -> total to (done[tagId] ?: 0) }
            }
            fun onTagTap(id: String) {
                if (selectionMode) {
                    if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
                } else {
                    onTagClick(id)
                }
            }
            if (tags.isEmpty()) {
                com.mj.yata.ui.widgets.TabEmptyState(
                    icon = Icons.AutoMirrored.Filled.Label,
                    title = "No tags yet",
                    subtitle = "Tag tasks to group them by topic, client, or anything you like.",
                    actionLabel = "New tag",
                    onAction = onNewTagClick
                )
            }
            fun List<Tag>.sorted() = sortedByEntityMode(
                sortMode,
                name = { it.name },
                starred = { it.starred },
                taskCount = { tagTaskCounts[it.id]?.first ?: 0 }
            )
            tagGroups.forEach { group ->
                val groupTags = tags.filter { it.groupId == group.id }.sorted()
                if (groupTags.isNotEmpty()) {
                    TagGroupSection(
                        title = group.name,
                        tags = groupTags,
                        taskCounts = tagTaskCounts,
                        onTagClick = ::onTagTap,
                        onToggleStar = onToggleStar,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        expanded = expandedGroups[group.id] ?: true,
                        onToggle = { expandedGroups[group.id] = !(expandedGroups[group.id] ?: true) },
                        onDelete = { onDeleteGroup(group) }
                    )
                }
            }
            val ungrouped = tags.filter { it.groupId == null || it.groupId !in groupedIds }.sorted()
            TagGroupSection(
                title = if (tagGroups.isEmpty()) null else "Ungrouped",
                tags = ungrouped,
                taskCounts = tagTaskCounts,
                onTagClick = ::onTagTap,
                onToggleStar = onToggleStar,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                trailing = { NewTagDashedRow(onNewTagClick) },
                expanded = expandedGroups["ungrouped"] ?: true,
                onToggle = { expandedGroups["ungrouped"] = !(expandedGroups["ungrouped"] ?: true) }
            )
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete ${selectedIds.size} tag(s)?") },
            text = { Text("Tasks keep their other tags. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onBulkDeleteTags(selectedIds.toList())
                    selectedIds.clear()
                    selectModeOn = false
                    showBulkDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TagGroupSection(
    title: String?,
    tags: List<Tag>,
    taskCounts: Map<String, Pair<Int, Int>>,
    onTagClick: (String) -> Unit,
    onToggleStar: (String) -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selectedIds: List<String> = emptyList()
) {
    if (tags.isEmpty() && trailing == null) return
    var showDeleteDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) {
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
                animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
                label = "groupChevron"
            )
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
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                            contentDescription = "Delete group",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(YataDur.sheet, easing = YataEase.emphDecel)) + fadeIn(tween(YataDur.fade)),
            exit = shrinkVertically(tween(YataDur.sheet, easing = YataEase.emphAccel)) + fadeOut(tween(YataDur.fade))
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tags.forEach { tag ->
                    val (total, done) = taskCounts[tag.id] ?: (0 to 0)
                    TagRow(
                        tag = tag,
                        totalTasks = total,
                        doneTasks = done,
                        onClick = { onTagClick(tag.id) },
                        onToggleStar = { onToggleStar(tag.id) },
                        selectionMode = selectionMode,
                        selected = selectedIds.contains(tag.id)
                    )
                }
                trailing?.invoke()
            }
        }
    }

    if (showDeleteDialog && onDelete != null && title != null) {
        com.mj.yata.ui.widgets.GroupDeleteConfirmDialog(
            groupTitle = title,
            entityLabel = "Tags",
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun TagRow(
    tag: Tag,
    totalTasks: Int,
    doneTasks: Int,
    onClick: () -> Unit,
    onToggleStar: () -> Unit = {},
    selectionMode: Boolean = false,
    selected: Boolean = false
) {
    val openTasks = totalTasks - doneTasks
    val progress = if (totalTasks > 0) doneTasks.toFloat() / totalTasks else 0f
    val accents = LocalYataAccents.current
    val tagColor = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selectionMode && selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
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
                        Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tagColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Label,
                    contentDescription = "Tag",
                    tint = tagColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                )
                Text(
                    text = if (openTasks == 1) "1 open" else "$openTasks open",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!selectionMode) {
                com.mj.yata.ui.widgets.StarToggleButton(
                    starred = tag.starred,
                    onToggle = onToggleStar,
                    starredColor = accents.accentD,
                    starredContentDescription = "Unstar tag",
                    unstarredContentDescription = "Star tag"
                )

                Spacer(modifier = Modifier.width(4.dp))

                com.mj.yata.ui.widgets.ProgressRing(
                    progress = progress,
                    size = 32.dp,
                    strokeWidth = 3.dp,
                    activeColor = tagColor
                )
            }
        }
    }
}

@Composable
private fun NewTagDashedRow(onClick: () -> Unit) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
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
                contentDescription = "New tag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "New tag",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
