package com.mj.yata.ui.screen.main.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.*
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar

@OptIn(ExperimentalLayoutApi::class)
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
                    contentDescription = "Open menu",
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
                        size = 32.dp,
                        photoUri = userPhotoUri
                    )
                }
            }
        }

        // 2. Title Header
        Text(
            text = "Tags",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

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
            val tagTaskCounts = remember(tasks, projects) {
                val counts = mutableMapOf<String, Int>()
                tasks.forEach { task ->
                    task.effectiveTagIds(projects).forEach { tagId ->
                        counts[tagId] = (counts[tagId] ?: 0) + 1
                    }
                }
                counts
            }
            tagGroups.forEach { group ->
                val groupTags = tags.filter { it.groupId == group.id }
                if (groupTags.isNotEmpty()) {
                    TagGroupSection(
                        title = group.name,
                        tags = groupTags,
                        taskCounts = tagTaskCounts,
                        onTagClick = onTagClick,
                        onToggleStar = onToggleStar,
                        expanded = expandedGroups[group.id] ?: true,
                        onToggle = { expandedGroups[group.id] = !(expandedGroups[group.id] ?: true) },
                        onDelete = { onDeleteGroup(group) }
                    )
                }
            }
            val ungrouped = tags.filter { it.groupId == null || it.groupId !in groupedIds }
            TagGroupSection(
                title = if (tagGroups.isEmpty()) null else "Ungrouped",
                tags = ungrouped,
                taskCounts = tagTaskCounts,
                onTagClick = onTagClick,
                onToggleStar = onToggleStar,
                trailing = { NewTagDashedPill(onNewTagClick) },
                expanded = expandedGroups["ungrouped"] ?: true,
                onToggle = { expandedGroups["ungrouped"] = !(expandedGroups["ungrouped"] ?: true) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroupSection(
    title: String?,
    tags: List<Tag>,
    taskCounts: Map<String, Int>,
    onTagClick: (String) -> Unit,
    onToggleStar: (String) -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
    onDelete: (() -> Unit)? = null
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tags.forEach { tag ->
                    TagPill(
                        tag = tag,
                        taskCount = taskCounts[tag.id] ?: 0,
                        onClick = { onTagClick(tag.id) },
                        onToggleStar = { onToggleStar(tag.id) }
                    )
                }
                trailing?.invoke()
            }
        }
    }

    if (showDeleteDialog && onDelete != null && title != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete \"$title\" group?") },
            text = { Text("Tags stay, they're just ungrouped.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagPill(tag: Tag, taskCount: Int, onClick: () -> Unit, onToggleStar: () -> Unit = {}) {
    val accents = LocalYataAccents.current
    val tagColor = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)

    Surface(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onToggleStar),
        shape = RoundedCornerShape(9999.dp),
        color = tagColor.copy(alpha = 0.16f),
        contentColor = tagColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val starScale = remember { Animatable(1f) }
            LaunchedEffect(tag.starred) {
                if (tag.starred) {
                    starScale.snapTo(0.4f)
                    starScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                }
            }
            if (tag.starred) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Starred",
                    tint = tagColor,
                    modifier = Modifier
                        .size(12.dp)
                        .scale(starScale.value)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(tagColor, RoundedCornerShape(9999.dp))
                )
            }
            Text(
                text = tag.name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp
                )
            )
            Surface(
                color = tagColor.copy(alpha = 0.24f),
                shape = RoundedCornerShape(9999.dp)
            ) {
                Text(
                    text = taskCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun NewTagDashedPill(onClick: () -> Unit) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9999.dp))
            .clickable { onClick() }
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )
                drawRoundRect(
                    color = outlineColor,
                    style = stroke,
                    cornerRadius = CornerRadius(9999.dp.toPx())
                )
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New tag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "New tag",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            )
        }
    }
}
