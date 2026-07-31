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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
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
    sortMode: com.mj.yata.util.EntitySortMode = com.mj.yata.util.EntitySortMode.NAME_ASC,
    onSortModeChange: (com.mj.yata.util.EntitySortMode) -> Unit = {},
    tagsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    var selectModeOn by remember { mutableStateOf(false) }
    val selectionMode = selectModeOn
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Top bar — swaps to a selection bar once tags are selected.
        if (selectionMode) {
            com.mj.yata.ui.widgets.TabSelectionTopBar(
                selectedCount = selectedIds.size,
                onCancel = { selectedIds.clear(); selectModeOn = false }
            ) {
                IconButton(onClick = { showBulkDeleteDialog = true }, enabled = selectedIds.isNotEmpty()) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tags_delete_selected), tint = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            com.mj.yata.ui.widgets.TabTopBar(
                title = stringResource(R.string.tab_tags),
                onMenuClick = onMenuClick,
                userName = userName,
                userPhotoUri = userPhotoUri,
                onProfileClick = onProfileClick
            ) {
                com.mj.yata.ui.widgets.EntitySortMenuButton(
                    current = sortMode,
                    onSelect = onSortModeChange,
                    contentDescription = stringResource(R.string.tags_sort_tags),
                    filledContainer = true
                )
                com.mj.yata.ui.widgets.YataTopBarIconButton(onClick = { selectModeOn = true }) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.action_select_tags)
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
            fun List<Tag>.sorted() = sortedByEntityMode(
                sortMode,
                name = { it.name },
                starred = { it.starred },
                taskCount = { tagTaskCounts[it.id]?.first ?: 0 },
                openTaskCount = { tagTaskCounts[it.id]?.let { (total, done) -> total - done } ?: 0 }
            )
            // A tag is "open" while any task carrying it is still not done — the same number the
            // rows print as "N open". Tags whose work is all finished (and tags nothing points at
            // any more) fall into Closed, which stays collapsed so they don't crowd out live ones.
            fun Tag.openTaskCount() = tagTaskCounts[id]?.let { (total, done) -> total - done } ?: 0
            val openTags = tags.filter { it.openTaskCount() > 0 }
            val closedTags = remember(tags, tagTaskCounts, sortMode) {
                tags.filter { it.openTaskCount() == 0 }.sorted()
            }
            var openExpanded by rememberSaveable { mutableStateOf(true) }
            var closedExpanded by rememberSaveable { mutableStateOf(false) }

            if (tags.isEmpty()) {
                com.mj.yata.ui.widgets.TabEmptyState(
                    icon = Icons.AutoMirrored.Filled.Label,
                    title = stringResource(R.string.tags_empty_title),
                    subtitle = stringResource(R.string.tags_empty_subtitle),
                    actionLabel = stringResource(R.string.tags_new_tag),
                    onAction = onNewTagClick
                )
            } else {
                CollapsibleTagSection(
                    title = stringResource(R.string.tags_section_open),
                    count = openTags.size,
                    expanded = openExpanded,
                    onToggle = { openExpanded = !openExpanded }
                ) {
                    // Groups keep their own subsections inside Open, so grouping and the
                    // open/closed split compose rather than one replacing the other.
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        tagGroups.forEach { group ->
                            val groupTags = openTags.filter { it.groupId == group.id }.sorted()
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
                        val ungrouped = openTags.filter { it.groupId == null || it.groupId !in groupedIds }.sorted()
                        TagGroupSection(
                            title = if (tagGroups.isEmpty()) null else "Ungrouped",
                            tags = ungrouped,
                            taskCounts = tagTaskCounts,
                            onTagClick = ::onTagTap,
                            onToggleStar = onToggleStar,
                            selectionMode = selectionMode,
                            selectedIds = selectedIds,
                            expanded = expandedGroups["ungrouped"] ?: true,
                            onToggle = { expandedGroups["ungrouped"] = !(expandedGroups["ungrouped"] ?: true) }
                        )
                    }
                }

                if (closedTags.isNotEmpty()) {
                    CollapsibleTagSection(
                        title = stringResource(R.string.tags_section_closed),
                        count = closedTags.size,
                        expanded = closedExpanded,
                        onToggle = { closedExpanded = !closedExpanded }
                    ) {
                        // Flat regardless of grouping: these are inactive, so a second level of
                        // group headers would be structure nobody needs to navigate.
                        TagGroupSection(
                            title = null,
                            tags = closedTags,
                            taskCounts = tagTaskCounts,
                            onTagClick = ::onTagTap,
                            onToggleStar = onToggleStar,
                            selectionMode = selectionMode,
                            selectedIds = selectedIds
                        )
                    }
                }
            }
        }
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(pluralStringResource(R.plurals.confirm_delete_tags_title, selectedIds.size, selectedIds.size)) },
            text = { Text(stringResource(R.string.tags_tasks_keep_their_other_tags_this_can_t_be)) },
            confirmButton = {
                TextButton(onClick = {
                    onBulkDeleteTags(selectedIds.toList())
                    selectedIds.clear()
                    selectModeOn = false
                    showBulkDeleteDialog = false
                }) {
                    Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/**
 * Top-level Open/Closed divider for the tag list. Deliberately styled a step heavier than
 * [TagGroupSection]'s header — group headers can render nested inside this one, and two
 * identical-looking headers stacked would read as siblings rather than parent and child.
 */
@Composable
private fun CollapsibleTagSection(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
        label = "tagSectionChevron"
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.tags_section_header_count, title, count),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse section" else "Expand section",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(YataDur.sheet, easing = YataEase.emphDecel)) + fadeIn(tween(YataDur.fade)),
            exit = shrinkVertically(tween(YataDur.sheet, easing = YataEase.emphAccel)) + fadeOut(tween(YataDur.fade))
        ) {
            content()
        }
    }
}

@Composable
private fun TagGroupSection(
    title: String?,
    tags: List<Tag>,
    taskCounts: Map<String, Pair<Int, Int>>,
    onTagClick: (String) -> Unit,
    onToggleStar: (String) -> Unit = {},
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selectedIds: List<String> = emptyList()
) {
    if (tags.isEmpty()) return
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
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_delete_group),
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
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_task_selected), tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            val tagLabel = stringResource(R.string.cd_tag_named, tag.name)
            com.mj.yata.ui.widgets.TagMonogram(
                name = tag.name,
                tagColor = tagColor,
                size = 40.dp,
                modifier = Modifier.semantics { contentDescription = tagLabel }
            )

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
