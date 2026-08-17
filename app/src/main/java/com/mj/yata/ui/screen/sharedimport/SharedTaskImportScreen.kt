package com.mj.yata.ui.screen.sharedimport

import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.sheets.NewTaskSheet
import com.mj.yata.ui.sheets.PendingSharedStructure
import com.mj.yata.ui.sheets.resolveAgainstLocalData
import com.mj.yata.util.export.parseTransferLink
import kotlinx.coroutines.launch

/**
 * Opens a single-task share link as a prefilled [NewTaskSheet] rather than importing it silently
 * — the receiver reviews and can change anything before it's written, and nothing touches the
 * database until Save. Multi-task links are still imported directly by MainActivity; routing here
 * only happens for a link parseTransferLink resolves to exactly one task.
 *
 * If the sender's list/project/tags don't exist locally yet, [PendingSharedStructure] surfaces
 * that before Save rather than creating them silently (docs/app-links-team-sharing-design.md §5).
 */
@Composable
fun SharedTaskImportScreen(
    viewModel: MainViewModel,
    link: String,
    onDismiss: () -> Unit,
    onImported: () -> Unit
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

    if (soleTask == null) {
        // Only reachable if the link stopped being parseable between MainActivity's routing
        // check and this screen composing (e.g. a malformed nav argument) — MainActivity already
        // verified there is exactly one task before navigating here.
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
        lists = lists,
        projects = activeProjects,
        people = activePeople,
        tags = tags,
        tasks = tasks,
        initialDraft = finalDraft,
        headerTitleRes = R.string.shared_task_import_title,
        onAddTask = { draft ->
            viewModel.addTask(draft)
            onImported()
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
