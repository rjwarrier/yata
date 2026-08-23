package com.mj.yata.ui.screen.sharedimport

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.sheets.NewTaskSheet
import com.mj.yata.ui.sheets.PendingSharedStructure
import com.mj.yata.ui.sheets.resolveAgainstLocalData
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.theme.yataItemFade
import com.mj.yata.ui.theme.yataItemPlacement
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.util.export.ParsedTransfer
import com.mj.yata.util.export.SharedTaskDraft
import com.mj.yata.util.export.TaskTransferImportResult
import com.mj.yata.util.export.TaskTransferImporter
import com.mj.yata.util.export.parseTransferLink
import kotlinx.coroutines.launch

/**
 * Opens share links behind a review step rather than importing them silently. Single-task links
 * become a prefilled [NewTaskSheet], where the receiver can change anything before Save; multi-task
 * links show a compact preview and require an explicit Import tap before the bulk write happens.
 *
 * If the sender's list/project/tags don't exist locally yet, [PendingSharedStructure] surfaces
 * that before Save rather than creating them silently (docs/app-links-team-sharing-design.md §5).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SharedTaskImportScreen(
    viewModel: MainViewModel,
    link: String,
    taskTransferImporter: TaskTransferImporter,
    onDismiss: () -> Unit,
    onImported: (TaskTransferImportResult) -> Unit
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val activeProjects by viewModel.activeProjects.collectAsStateWithLifecycle()
    val activePeople by viewModel.activePeople.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle()
    val voiceLanguage by viewModel.voiceRecognitionLanguage.collectAsStateWithLifecycle()
    val defaultDueDate by viewModel.defaultDueDate.collectAsStateWithLifecycle()

    // Parsing is pure and cheap (see docs/app-links-reliability-plan.md's Phase 0 split), so
    // re-running it here rather than passing the already-parsed draft through nav arguments
    // keeps this screen self-contained across process death and back-stack restoration.
    val parsed = remember(link) {
        runCatching { parseTransferLink(Uri.parse(link)) }.getOrNull()
    }
    val soleTask = parsed?.tasks?.singleOrNull()

    if (parsed != null && soleTask == null) {
        MultiTaskImportPreview(
            parsed = parsed,
            importUri = Uri.parse(link),
            lists = lists,
            projects = projects,
            tags = tags,
            taskTransferImporter = taskTransferImporter,
            onDismiss = onDismiss,
            onImported = onImported
        )
        return
    }

    if (soleTask == null) {
        // Only reachable if the link stopped being parseable between MainActivity's routing
        // check and this screen composing (e.g. a malformed nav argument).
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val resolved = remember(soleTask, lists, projects, tags) {
        soleTask.resolveAgainstLocalData(lists, projects, tags)
    }

    // Null = not yet decided, so the confirm dialog gates Save whenever there's something to
    // confirm. A link with nothing missing skips straight past it — the common case, since
    // lists/projects/tags are usually already shared vocabulary between teammates.
    var decision by rememberSaveable { mutableStateOf<StructureDecision?>(null) }
    LaunchedEffect(resolved.pending.isEmpty) {
        if (resolved.pending.isEmpty) decision = StructureDecision.Skip
    }

    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var createdListId by rememberSaveable { mutableStateOf<String?>(null) }
    var createdProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    val createdTagIds = remember { mutableStateListOf<String>() }

    if (decision == null) {
        PendingStructureDialog(
            pending = resolved.pending,
            creating = creating,
            onCreateAndAdd = {
                creating = true
                scope.launch {
                    // resolveAgainstLocalData already confirmed these names don't exist locally,
                    // so nothing here needs to re-check for a match — every name in `pending` is
                    // a genuine creation, not a race to resolve.
                    resolved.pending.listName?.let { name ->
                        val id = "import_list_" + java.util.UUID.randomUUID()
                        viewModel.upsertList(YataList(id = id, name = name, color = "accentA", icon = "folder"))
                        createdListId = id
                    }
                    resolved.pending.projectName?.let { name ->
                        val id = "import_project_" + java.util.UUID.randomUUID()
                        viewModel.upsertProject(Project(id = id, name = name, color = "accentA", icon = "layers"))
                        createdProjectId = id
                    }
                    resolved.pending.tagNames.forEach { name ->
                        val id = "import_tag_" + java.util.UUID.randomUUID()
                        viewModel.upsertTag(Tag(id = id, name = name, color = "accentA"))
                        createdTagIds.add(id)
                    }
                    creating = false
                    decision = StructureDecision.CreateAndAdd
                }
            },
            onSkip = { decision = StructureDecision.Skip },
            onDismissRequest = onDismiss
        )
        return
    }

    val finalDraft = remember(decision, resolved, createdListId, createdProjectId, createdTagIds.toList()) {
        if (decision == StructureDecision.CreateAndAdd) {
            resolved.draft.copy(
                listId = resolved.draft.listId ?: createdListId,
                projectId = resolved.draft.projectId ?: createdProjectId,
                tagIds = resolved.draft.tagIds + createdTagIds
            )
        } else {
            resolved.draft
        }
    }

    NewTaskSheet(
        dueDatePickerContext = com.mj.yata.ui.widgets.rememberDueDatePickerContext(viewModel),
        lists = lists,
        projects = activeProjects,
        people = activePeople,
        tags = tags,
        tasks = tasks,
        initialDraft = finalDraft,
        headerTitleRes = R.string.shared_task_import_title,
        onAddTask = { draft ->
            viewModel.addTask(draft)
            onImported(TaskTransferImportResult(taskCount = 1, copiedStructure = parsed?.copyStructure == true))
        },
        onCreateTag = { id, name, color -> viewModel.upsertTag(Tag(id = id, name = name, color = color)) },
        onCreatePerson = { _, _, _ -> }, // people are never created from a shared link — see resolveAgainstLocalData
        onDismiss = onDismiss,
        projectsEnabled = projectsFeatureEnabled,
        tagsEnabled = tagsFeatureEnabled,
        peopleEnabled = peopleFeatureEnabled,
        voiceLanguage = voiceLanguage,
        defaultDueDate = defaultDueDate
    )
}

private enum class StructureDecision { CreateAndAdd, Skip }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MultiTaskImportPreview(
    parsed: ParsedTransfer,
    importUri: Uri,
    lists: List<YataList>,
    projects: List<Project>,
    tags: List<Tag>,
    taskTransferImporter: TaskTransferImporter,
    onDismiss: () -> Unit,
    onImported: (TaskTransferImportResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    var importing by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val missing = remember(parsed, lists, projects, tags) {
        parsed.missingStructure(lists, projects, tags)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.shared_tasks_import_title),
                        style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSynthesis = FontSynthesis.All)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !importing) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !importing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            importing = true
                            failed = false
                            scope.launch {
                                runCatching { taskTransferImporter.importFrom(importUri) }
                                    .onSuccess(onImported)
                                    .onFailure {
                                        failed = true
                                        importing = false
                                    }
                            }
                        },
                        enabled = !importing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (importing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.shared_tasks_import_action))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        AdaptiveContentBox(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "summary") {
                    MultiTaskImportSummaryCard(
                        taskCount = parsed.tasks.size,
                        copyStructure = parsed.copyStructure,
                        missing = missing,
                        failed = failed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = yataItemFade,
                                placementSpec = yataItemPlacement,
                                fadeOutSpec = yataItemFade
                            )
                    )
                }
                items(parsed.tasks, key = { it.title + it.hashCode().toString() }) { task ->
                    SharedTaskPreviewCard(
                        task = task,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = yataItemFade,
                                placementSpec = yataItemPlacement,
                                fadeOutSpec = yataItemFade
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiTaskImportSummaryCard(
    taskCount: Int,
    copyStructure: Boolean,
    missing: PendingSharedStructure,
    failed: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.animateContentSize(animationSpec = tween(YataDur.sheet, easing = YataEase.emphasized))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(28.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(R.string.shared_tasks_import_count, taskCount),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (copyStructure) {
                            stringResource(R.string.shared_tasks_import_copy_structure)
                        } else {
                            stringResource(R.string.shared_tasks_import_inbox_only)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (copyStructure) {
                val names = listOfNotNull(missing.listName, missing.projectName) + missing.tagNames
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (names.isEmpty()) {
                        ImportInfoChip(
                            label = stringResource(R.string.shared_tasks_import_structure_ready),
                            icon = Icons.AutoMirrored.Default.Label
                        )
                    } else {
                        names.take(6).forEach { name ->
                            ImportInfoChip(label = name, icon = Icons.AutoMirrored.Default.Label)
                        }
                        if (names.size > 6) {
                            ImportInfoChip(
                                label = stringResource(R.string.shared_tasks_import_more_structure, names.size - 6),
                                icon = Icons.AutoMirrored.Default.Label
                            )
                        }
                    }
                }
            }
            if (failed) {
                Text(
                    text = stringResource(R.string.task_transfer_import_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SharedTaskPreviewCard(
    task: SharedTaskDraft,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(9.dp)
                            .size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    task.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                task.due?.let { ImportInfoChip(label = it, icon = Icons.Default.Today) }
                if (task.priority != "none") ImportInfoChip(label = task.priority, icon = Icons.AutoMirrored.Default.Label)
                task.list?.name?.let { ImportInfoChip(label = it, icon = Icons.Default.Inbox) }
                task.project?.name?.let { ImportInfoChip(label = it, icon = Icons.AutoMirrored.Default.PlaylistAdd) }
                task.tags.take(3).forEach { tag -> ImportInfoChip(label = tag.name, icon = Icons.AutoMirrored.Default.Label) }
            }
        }
    }
}

@Composable
private fun ImportInfoChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun ParsedTransfer.missingStructure(
    lists: List<YataList>,
    projects: List<Project>,
    tags: List<Tag>
): PendingSharedStructure {
    if (!copyStructure) return PendingSharedStructure(listName = null, projectName = null, tagNames = emptyList())

    fun knownNames(values: List<String>) = values.map { it.normalizedName() }.toSet()
    fun missing(
        names: Sequence<String?>,
        known: Set<String>
    ): List<String> = names
        .filterNotNull()
        .map { it.trim() }
        .filter { it.isNotBlank() && it.normalizedName() !in known }
        .distinctBy { it.normalizedName() }
        .toList()

    val missingLists = missing(tasks.asSequence().map { it.list?.name }, knownNames(lists.map { it.name }))
    val missingProjects = missing(tasks.asSequence().map { it.project?.name }, knownNames(projects.map { it.name }))
    val missingTags = missing(tasks.asSequence().flatMap { it.tags.asSequence().map { tag -> tag.name } }, knownNames(tags.map { it.name }))
    return PendingSharedStructure(
        listName = missingLists.firstOrNull(),
        projectName = missingProjects.firstOrNull(),
        tagNames = (missingLists.drop(1) + missingProjects.drop(1) + missingTags).distinctBy { it.normalizedName() }
    )
}

private fun String.normalizedName(): String = trim().lowercase()

@Composable
private fun PendingStructureDialog(
    pending: PendingSharedStructure,
    creating: Boolean,
    onCreateAndAdd: () -> Unit,
    onSkip: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val names = listOfNotNull(pending.listName, pending.projectName) + pending.tagNames
    AlertDialog(
        onDismissRequest = { if (!creating) onDismissRequest() },
        title = { Text(stringResource(R.string.shared_task_import_create_missing_title)) },
        text = {
            Text(
                stringResource(
                    R.string.shared_task_import_create_missing_message,
                    names.joinToString(", ") { "“$it”" }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onCreateAndAdd, enabled = !creating) {
                Text(stringResource(R.string.shared_task_import_create_and_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip, enabled = !creating) {
                Text(stringResource(R.string.shared_task_import_skip))
            }
        }
    )
}
