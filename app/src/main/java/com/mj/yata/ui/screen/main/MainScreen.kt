package com.mj.yata.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mj.yata.ui.widgets.showUndoSnackbar
import com.mj.yata.R
import com.mj.yata.domain.model.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.ui.navigation.Screen
import com.mj.yata.ui.screen.main.tabs.*
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.yataItemFade
import com.mj.yata.ui.theme.yataItemPlacement
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.PressableScaleBox
import com.mj.yata.ui.sheets.*
import kotlinx.coroutines.launch

enum class MainSheetType { None, NewTask, NewProject, NewPerson, NewTag }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    navController: NavController,
    onNavigateToSettings: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToNextDays: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSavedSearch: (String) -> Unit = {},
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToProjectDetail: (String) -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    onNavigateToTagDetail: (String) -> Unit,
    onNavigateToListDetail: (String) -> Unit,
    initialTab: Int = -1,
    initialShowNewTaskSheet: Boolean = false,
    initialQuickAddListId: String? = null
) {
    val scope = rememberCoroutineScope()
    val undoWindowSeconds = com.mj.yata.ui.widgets.LocalUndoWindowSeconds.current
    val defaultDueDate by viewModel.defaultDueDate.collectAsStateWithLifecycle()
    val autoAssignToMe by viewModel.autoAssignToMe.collectAsStateWithLifecycle()
    val defaultPriority by viewModel.defaultPriority.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    // Manual cloud sync from the Today top bar. Lives here rather than in TodayTab because the
    // SnackbarHostState that reports the result belongs to this Scaffold, and because `syncing`
    // has to survive the tab switching underneath it.
    val cloudBackupEnabled by viewModel.cloudBackupEnabled.collectAsStateWithLifecycle()
    var syncing by remember { mutableStateOf(false) }
    val syncSuccessMessage = stringResource(R.string.sync_success)
    val syncFailedMessage = stringResource(R.string.sync_failed)

    fun runManualSync() {
        // Guarded rather than queued: repeated taps during a slow upload should do nothing, not
        // stack up duplicate backups of the same data.
        if (syncing) return
        syncing = true
        viewModel.cloudBackupNow { result ->
            syncing = false
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) syncSuccessMessage else syncFailedMessage
                )
            }
        }
    }

    // Bulk delete is deferred until the Undo snackbar times out, mirroring the single-task
    // delete flow on TaskDetailScreen — the coroutine outlives the confirm dialog that triggered it.
    fun bulkDeleteWithUndo(ids: List<String>) {
        if (ids.isEmpty()) return
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, if (ids.size == 1) "Task deleted" else "${ids.size} tasks deleted", undoWindowSeconds)
            if (!result) {
                viewModel.bulkDeleteTasks(ids)
            }
        }
    }

    // Main tabs state: 0=Today, 1=Projects, 2=People, 3=Tags, 4=Upcoming (Week/Month toggle inside)
    var selectedTab by rememberSaveable { mutableIntStateOf(if (initialTab >= 0) initialTab.coerceIn(0, 4) else 0) }
    var restoredHomeTab by remember { mutableStateOf(initialTab >= 0) }
    var calendarSelectedDay by remember { mutableStateOf(java.time.LocalDate.now()) }

    // Confetti trigger
    var celebrateTrigger by remember { mutableIntStateOf(0) }

    // Sheet states
    var activeSheet by rememberSaveable { mutableStateOf(MainSheetType.None) }
    var newTaskHasDraft by rememberSaveable { mutableStateOf(false) }
    var showDiscardNewTaskDialog by rememberSaveable { mutableStateOf(false) }
    var isNewListSheetOpen by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }

    // "Quick Add" launcher shortcut / widget tap lands here with this set — open the sheet once,
    // pre-selecting a list if the Quick Add widget's list chip was what was tapped.
    LaunchedEffect(initialShowNewTaskSheet) {
        if (initialShowNewTaskSheet) activeSheet = MainSheetType.NewTask
    }

    // Database updates flows
    val uiState by viewModel.mainScreenUiState.collectAsStateWithLifecycle()
    val tasks = uiState.tasks
    val projects = uiState.projects
    val activeProjects = uiState.activeProjects
    val lists = uiState.lists
    val people = uiState.people
    val activePeople = uiState.activePeople
    val tags = uiState.tags
    val tagGroups = uiState.tagGroups
    val personGroups = uiState.personGroups

    // Preferences
    val userName = uiState.userName
    val userEmail = uiState.userEmail
    val userPhotoUri = uiState.userPhotoUri
    val startOfWeekSunday = uiState.startOfWeekSunday
    val peopleFeatureEnabled = uiState.peopleFeatureEnabled
    val tagsFeatureEnabled = uiState.tagsFeatureEnabled
    val projectsFeatureEnabled = uiState.projectsFeatureEnabled
    val taskRowDensity = uiState.taskRowDensity
    val todayTabEnabled = uiState.todayTabEnabled
    val upcomingTabEnabled = uiState.upcomingTabEnabled
    val fabPosition = uiState.fabPosition
    val hideCompletedToday = uiState.hideCompletedToday
    val todayBadgeCount = uiState.todayRemainingCount
    val sortModeToday by viewModel.sortModeToday.collectAsStateWithLifecycle()
    val sortModeTagsTab by viewModel.sortModeTagsTab.collectAsStateWithLifecycle()
    val sortModePeopleTab by viewModel.sortModePeopleTab.collectAsStateWithLifecycle()
    val recentTasks by viewModel.recentTasks.collectAsStateWithLifecycle()
    val lastHomeTab by viewModel.lastHomeTab.collectAsStateWithLifecycle()
    val savedSmartFilterSets by viewModel.savedSmartFilterSets.collectAsStateWithLifecycle()
    val voiceLanguage by viewModel.voiceRecognitionLanguage.collectAsStateWithLifecycle()

    val startupTab by viewModel.startupTab.collectAsStateWithLifecycle()
    val confettiEnabled by viewModel.confettiEnabled.collectAsStateWithLifecycle()
    val todayShowUpcomingWhenEmpty by viewModel.todayShowUpcomingWhenEmpty.collectAsStateWithLifecycle()

    fun isTabAvailable(tabId: Int): Boolean = when (tabId) {
        0 -> todayTabEnabled
        1 -> projectsFeatureEnabled
        2 -> peopleFeatureEnabled
        3 -> tagsFeatureEnabled
        4 -> upcomingTabEnabled
        else -> false
    }

    LaunchedEffect(lastHomeTab, startupTab, restoredHomeTab) {
        if (!restoredHomeTab) {
            // A fixed startup tab wins over the remembered one. It's still validated against the
            // feature flags: tab ids are fixed regardless of which tabs are hidden, so a startup
            // tab pointing at a disabled one would otherwise open a tab that isn't there.
            val requested = if (startupTab == StartupTab.LAST_USED) lastHomeTab else startupTab.tabId
            selectedTab = if (isTabAvailable(requested)) requested else 0
            restoredHomeTab = true
        }
    }

    LaunchedEffect(selectedTab, restoredHomeTab) {
        if (restoredHomeTab) viewModel.setLastHomeTab(selectedTab)
    }

    fun toggleDoneWithUndo(id: String) {
        val previous = tasks.find { it.id == id } ?: return
        viewModel.toggleTaskDone(id) { if (!previous.done) celebrateTrigger++ }
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, if (previous.done) "Task marked open" else "Task completed", undoWindowSeconds)
            if (result) {
                viewModel.restoreTasks(listOf(previous))
            }
        }
    }

    fun bulkCompleteWithUndo(ids: List<String>) {
        val previous = tasks.filter { it.id in ids }
        if (previous.isEmpty()) return
        viewModel.bulkCompleteTasks(ids)
        scope.launch {
            val result = showUndoSnackbar(snackbarHostState, "${previous.size} task(s) completed", undoWindowSeconds)
            if (result) {
                viewModel.restoreTasks(previous)
            }
        }
    }

    // If the tab currently open gets disabled out from under the user, fall back to Today
    // rather than leaving them stranded on a tab no longer reachable from the nav bar.
    LaunchedEffect(peopleFeatureEnabled, tagsFeatureEnabled, projectsFeatureEnabled) {
        if ((selectedTab == 1 && !projectsFeatureEnabled) ||
            (selectedTab == 2 && !peopleFeatureEnabled) ||
            (selectedTab == 3 && !tagsFeatureEnabled)
        ) {
            selectedTab = 0
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(320.dp)
            ) {
                val accents = LocalYataAccents.current

                // Starred projects, folders, tags & people (computed once per data change, used below)
                val starredProjects = remember(activeProjects) { activeProjects.filter { it.starred } }
                val starredLists = remember(lists) { lists.filter { it.starred } }
                val starredTags = remember(tags) { tags.filter { it.starred } }
                val starredPeople = remember(activePeople) { activePeople.filter { it.starred } }
                val visibleStarredProjects = if (projectsFeatureEnabled) starredProjects else emptyList()
                val visibleStarredTags = if (tagsFeatureEnabled) starredTags else emptyList()
                val visibleStarredPeople = if (peopleFeatureEnabled) starredPeople else emptyList()

                // Main content scrolls in its own weighted region so it never pushes Analytics/
                // Settings off-screen — that footer is a fixed sibling below, not a LazyColumn
                // item, so it sits flush at the bottom when content is short (nothing to scroll)
                // and stays fully visible (never clipped) when content overflows and scrolls.
                Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        // Profile Header
                        // Tapping the header goes to Settings, where these are edited. Until a
                        // name/email is set both fields render as placeholders rather than empty
                        // strings — an unset profile otherwise showed as a bare avatar with a
                        // blank column beside it, giving no hint the fields exist at all.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    onNavigateToSettings()
                                }
                                .padding(vertical = 4.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            PersonAvatar(
                                initials = com.mj.yata.util.initialsFor(userName),
                                accentKey = "accentC",
                                size = 44.dp,
                                photoUri = userPhotoUri
                            )
                            Column {
                                Text(
                                    text = userName.ifBlank { stringResource(R.string.profile_add_name) },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (userName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = userEmail.ifBlank { stringResource(R.string.profile_add_email) },
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        DrawerItem("Today", Icons.Default.Today, selectedTab == 0) {
                            selectedTab = 0
                            scope.launch { drawerState.close() }
                        }
                    }
                    if (projectsFeatureEnabled) {
                        item {
                            DrawerItem("Projects", Icons.Default.Layers, selectedTab == 1) {
                                selectedTab = 1
                                scope.launch { drawerState.close() }
                            }
                        }
                    }
                    if (peopleFeatureEnabled) {
                        item {
                            DrawerItem("People", Icons.Default.People, selectedTab == 2) {
                                selectedTab = 2
                                scope.launch { drawerState.close() }
                            }
                        }
                    }
                    if (tagsFeatureEnabled) {
                        item {
                            DrawerItem("Tags", Icons.AutoMirrored.Filled.Label, selectedTab == 3) {
                                selectedTab = 3
                                scope.launch { drawerState.close() }
                            }
                        }
                    }
                    item {
                        DrawerItem("Upcoming", Icons.Default.CalendarViewWeek, selectedTab == 4) {
                            selectedTab = 4
                            scope.launch { drawerState.close() }
                        }
                    }
                    // One entry rather than the old collapsible "Tools" section, which put eight
                    // items behind a header and made the drawer the longest surface in the app.
                    // Everything that lived there is in the palette itself now — including the six
                    // saved searches (My Work, Focus Mode, the two Reviews, Stale Nudges, Task
                    // Health), which are searchable there instead of needing to be memorised as
                    // menu positions. Next 10 Days is also on Today's top bar, so it lost nothing.
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        DrawerItem("Command palette", Icons.Default.Bolt, false) {
                            showCommandPalette = true
                            scope.launch { drawerState.close() }
                        }
                    }

                    if (savedSmartFilterSets.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.drawer_custom_views),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        items(savedSmartFilterSets.sorted(), key = { "smart_$it" }) { encoded ->
                            DrawerItem(
                                label = encoded.smartFilterSetLabel(),
                                icon = Icons.Default.FilterList,
                                selected = false
                            ) {
                                onNavigateToSavedSearch(encoded)
                                scope.launch { drawerState.close() }
                            }
                        }
                    }

                    // Starred projects, folders, tags & people section
                    if (visibleStarredProjects.isNotEmpty() || starredLists.isNotEmpty() || visibleStarredTags.isNotEmpty() || visibleStarredPeople.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.drawer_starred),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        items(visibleStarredProjects, key = { "starred_pr_${it.id}" }) { project ->
                            DrawerItem(
                                label = project.name,
                                icon = Icons.Default.Layers,
                                selected = false,
                                accentColor = accents.getAccent(project.color),
                                modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                                )
                            ) {
                                onNavigateToProjectDetail(project.id)
                                scope.launch { drawerState.close() }
                            }
                        }
                        items(starredLists, key = { "starred_l_${it.id}" }) { list ->
                            DrawerItem(
                                label = list.name,
                                icon = Icons.Default.Folder,
                                selected = false,
                                accentColor = accents.getAccent(list.color),
                                modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                                )
                            ) {
                                onNavigateToListDetail(list.id)
                                scope.launch { drawerState.close() }
                            }
                        }
                        items(visibleStarredTags, key = { "starred_t_${it.id}" }) { tag ->
                            val tagColor = if (tag.color == "error") MaterialTheme.colorScheme.error else accents.getAccent(tag.color)
                            DrawerItem(
                                label = tag.name,
                                icon = Icons.AutoMirrored.Filled.Label,
                                selected = false,
                                accentColor = tagColor,
                                modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                                )
                            ) {
                                onNavigateToTagDetail(tag.id)
                                scope.launch { drawerState.close() }
                            }
                        }
                        items(visibleStarredPeople, key = { "starred_p_${it.id}" }) { person ->
                            DrawerItem(
                                label = person.name,
                                icon = Icons.Default.Person,
                                selected = false,
                                modifier = Modifier.animateItem(fadeInSpec = yataItemFade, placementSpec = yataItemPlacement, fadeOutSpec = yataItemFade
                                )
                            ) {
                                onNavigateToPersonDetail(person.id)
                                scope.launch { drawerState.close() }
                            }
                        }
                    }

                    // Folder lists section
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.drawer_lists),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        isNewListSheetOpen = true
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.main_new_list),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    item(key = "lists_dnd") {
                        val sortedLists = remember(lists) { lists.sortedBy { it.sortOrder } }
                        var localListOrder by remember { mutableStateOf(sortedLists) }
                        var isDraggingLists by remember { mutableStateOf(false) }
                        LaunchedEffect(sortedLists) {
                            if (!isDraggingLists) localListOrder = sortedLists
                        }
                        com.mj.yata.ui.widgets.DragReorderColumn(
                            items = localListOrder,
                            key = { it.id },
                            onMove = { from, to -> localListOrder = localListOrder.toMutableList().apply { add(to, removeAt(from)) } },
                            onDragEnd = { viewModel.commitListOrder(localListOrder) },
                            onDragStateChanged = { isDraggingLists = it }
                        ) { list ->
                            DrawerItem(
                                label = list.name,
                                icon = Icons.Default.Folder,
                                selected = false,
                                accentColor = accents.getAccent(list.color)
                            ) {
                                onNavigateToListDetail(list.id)
                                scope.launch { drawerState.close() }
                            }
                        }
                    }

                }

                // Fixed footer (not a LazyColumn item) — always flush with the bottom of the
                // drawer, whether or not the content above it needed to scroll.
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    DrawerItem("Analytics", Icons.Default.Analytics, false) {
                        onNavigateToAnalytics()
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem("Settings", Icons.Default.Settings, false) {
                        onNavigateToSettings()
                        scope.launch { drawerState.close() }
                    }
                }
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data -> com.mj.yata.ui.widgets.YataSnackbar(data) }
            },
            floatingActionButtonPosition = if (fabPosition == com.mj.yata.domain.model.FabPosition.LEFT) {
                androidx.compose.material3.FabPosition.Start
            } else {
                androidx.compose.material3.FabPosition.End
            },
            bottomBar = {
                CustomBottomNav(
                    selectedTab = selectedTab,
                    todayBadgeCount = todayBadgeCount,
                    peopleEnabled = peopleFeatureEnabled,
                    tagsEnabled = tagsFeatureEnabled,
                    projectsEnabled = projectsFeatureEnabled,
                    todayEnabled = todayTabEnabled,
                    upcomingEnabled = upcomingTabEnabled,
                    onTabSelected = { selectedTab = it }
                )
            },
            floatingActionButton = {
                // Each tab offers its own primary creation action; others show no FAB.
                val fabTarget = when (selectedTab) {
                    0 -> "New task" to MainSheetType.NewTask
                    1 -> "New project" to MainSheetType.NewProject
                    2 -> "Add person" to MainSheetType.NewPerson
                    3 -> "New tag" to MainSheetType.NewTag
                    4 -> "New task" to MainSheetType.NewTask
                    else -> null
                }

                AnimatedVisibility(
                    visible = fabTarget != null && fabPosition != com.mj.yata.domain.model.FabPosition.HIDDEN,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    val (fabLabel, sheetType) = fabTarget ?: ("New task" to MainSheetType.NewTask)
                    PressableScaleBox(
                        onClick = {
                            activeSheet = sheetType
                        },
                        // No navigationBarsPadding here. CustomBottomNav already consumes the
                        // system navigation inset in both modes — the floating variant on its
                        // outer Box, the docked variant on its inner one — and Scaffold positions
                        // the FAB relative to the bottomBar's *outer* height. Applying the inset
                        // again added the full nav-bar height a second time, which is why the FAB
                        // floated well clear of the panel: ~24dp on gesture nav, ~48dp with the
                        // three-button bar. Scaffold's own 16dp FAB-to-bottomBar spacing is the
                        // only gap needed, and it's the M3 default.
                        modifier = Modifier
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 22.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                androidx.compose.animation.AnimatedContent(
                                    targetState = fabLabel,
                                    transitionSpec = {
                                        (androidx.compose.animation.slideInVertically { height -> height } + fadeIn()).togetherWith(
                                            androidx.compose.animation.slideOutVertically { height -> -height } + fadeOut()
                                        )
                                    },
                                    label = "fabLabelAnim"
                                ) { targetLabel ->
                                    Text(targetLabel, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.isCtrlPressed) {
                            when (event.key) {
                                Key.K -> {
                                    showCommandPalette = true
                                    true
                                }
                                Key.N -> {
                                    activeSheet = MainSheetType.NewTask
                                    true
                                }
                                Key.F -> {
                                    onNavigateToSearch()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            false
                        }
                    }
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                // Tab switcher with dynamic M3 slide-fade animations
                androidx.compose.animation.AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val direction = if (targetState > initialState) {
                            androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        slideIntoContainer(
                            towards = direction,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = com.mj.yata.ui.theme.YataDur.nav, easing = com.mj.yata.ui.theme.YataEase.emphasized)
                        ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(com.mj.yata.ui.theme.YataDur.nav, easing = com.mj.yata.ui.theme.YataEase.emphDecel)) togetherWith
                        slideOutOfContainer(
                            towards = direction,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = com.mj.yata.ui.theme.YataDur.nav, easing = com.mj.yata.ui.theme.YataEase.emphasized)
                        ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(com.mj.yata.ui.theme.YataDur.fade))
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> TodayTab(
                            tasks = tasks,
                            lists = lists,
                            projects = projects,
                            people = people,
                            tags = tags,
                            userName = userName,
                            userPhotoUri = userPhotoUri,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onNextDaysClick = onNavigateToNextDays,
                            onNewTaskClick = { activeSheet = MainSheetType.NewTask },
                            onProfileClick = onNavigateToSettings,
                            onTaskClick = onNavigateToTaskDetail,
                            onToggleDone = { toggleDoneWithUndo(it) },
                            onQuickSnooze = { id, preset -> viewModel.quickSnoozeTask(id, preset) },
                            onSwipeToDelete = { bulkDeleteWithUndo(listOf(it)) },
                            onBulkComplete = { bulkCompleteWithUndo(it) },
                            onBulkDelete = { bulkDeleteWithUndo(it) },
                            onBulkAddTag = { ids, tagId -> viewModel.bulkAddTag(ids, tagId) },
                            onBulkSetProject = { ids, projectId -> viewModel.bulkSetProject(ids, projectId) },
                            onBulkSetList = { ids, listId -> viewModel.bulkSetList(ids, listId) },
                            onBulkDuplicate = { viewModel.bulkDuplicateTasks(it) },
                            onBulkAssignPerson = { ids, personId -> viewModel.bulkAssignPerson(ids, personId) },
                            onBulkReschedule = { ids, preset -> viewModel.bulkRescheduleTasks(ids, preset) },
                            onRenameTask = { id, title -> viewModel.renameTask(id, title) },
                            onAddComment = { taskId, body -> viewModel.addComment(taskId, body) },
                            peopleEnabled = peopleFeatureEnabled,
                            tagsEnabled = tagsFeatureEnabled,
                            projectsEnabled = projectsFeatureEnabled,
                            taskRowDensity = taskRowDensity,
                            hideCompleted = hideCompletedToday,
                            onHideCompletedChange = { viewModel.setHideCompletedToday(it) },
                            sortMode = sortModeToday,
                            onSortModeChange = { viewModel.setSortModeToday(it) },
                            cloudSyncEnabled = cloudBackupEnabled,
                            confettiEnabled = confettiEnabled,
                            syncing = syncing,
                            onSyncClick = { runManualSync() },
                            showUpcomingWhenEmpty = todayShowUpcomingWhenEmpty
                        )
                        1 -> ProjectsTab(
                            projects = projects,
                            lists = lists,
                            tasks = tasks,
                            people = people,
                            userName = userName,
                            userPhotoUri = userPhotoUri,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onProfileClick = onNavigateToSettings,
                            onProjectClick = onNavigateToProjectDetail,
                            onNewProjectClick = { activeSheet = MainSheetType.NewProject },
                            onToggleProjectStar = { viewModel.toggleProjectStarred(it) },
                            onProjectsReordered = { viewModel.commitProjectOrder(it) },
                            onBulkArchiveProjects = { viewModel.bulkArchiveProjects(it) },
                            peopleEnabled = peopleFeatureEnabled
                        )
                        2 -> PeopleTab(
                            people = people,
                            personGroups = personGroups,
                            tasks = tasks,
                            userName = userName,
                            userPhotoUri = userPhotoUri,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onProfileClick = onNavigateToSettings,
                            onPersonClick = onNavigateToPersonDetail,
                            onAddPersonClick = { activeSheet = MainSheetType.NewPerson },
                            onAssignGroup = { personIds, groupId -> viewModel.setPeopleGroup(personIds, groupId) },
                            onCreateGroupAndAssign = { id, name, personIds ->
                                viewModel.upsertPersonGroup(PersonGroup(id = id, name = name, color = "accentC"))
                                viewModel.setPeopleGroup(personIds, id)
                            },
                            onToggleStar = { viewModel.togglePersonStarred(it) },
                            onDeleteGroup = { viewModel.deletePersonGroup(it) },
                            sortMode = sortModePeopleTab,
                            onSortModeChange = { viewModel.setSortModePeopleTab(it) }
                        )
                        3 -> TagsTab(
                            tags = tags,
                            tagGroups = tagGroups,
                            tasks = tasks,
                            lists = lists,
                            projects = projects,
                            userName = userName,
                            userPhotoUri = userPhotoUri,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onProfileClick = onNavigateToSettings,
                            onTagClick = onNavigateToTagDetail,
                            onNewTagClick = { activeSheet = MainSheetType.NewTag },
                            onToggleStar = { viewModel.toggleTagStarred(it) },
                            onDeleteGroup = { viewModel.deleteTagGroup(it) },
                            onBulkDeleteTags = { viewModel.bulkDeleteTags(it) },
                            tagsEnabled = tagsFeatureEnabled,
                            sortMode = sortModeTagsTab,
                            onSortModeChange = { viewModel.setSortModeTagsTab(it) }
                        )
                        4 -> UpcomingTab(
                            tasks = tasks,
                            lists = lists,
                            projects = projects,
                            people = people,
                            tags = tags,
                            userName = userName,
                            userPhotoUri = userPhotoUri,
                            selectedDay = calendarSelectedDay,
                            onSelectedDayChange = { calendarSelectedDay = it },
                            startOfWeekSunday = startOfWeekSunday,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onProfileClick = onNavigateToSettings,
                            onTaskClick = onNavigateToTaskDetail,
                            onToggleDone = { toggleDoneWithUndo(it) },
                            onQuickSnooze = { id, preset -> viewModel.quickSnoozeTask(id, preset) },
                            onSwipeToDelete = { bulkDeleteWithUndo(listOf(it)) },
                            onBulkComplete = { bulkCompleteWithUndo(it) },
                            onBulkDelete = { bulkDeleteWithUndo(it) },
                            onBulkAddTag = { ids, tagId -> viewModel.bulkAddTag(ids, tagId) },
                            onBulkSetProject = { ids, projectId -> viewModel.bulkSetProject(ids, projectId) },
                            onBulkSetList = { ids, listId -> viewModel.bulkSetList(ids, listId) },
                            onBulkDuplicate = { viewModel.bulkDuplicateTasks(it) },
                            onBulkAssignPerson = { ids, personId -> viewModel.bulkAssignPerson(ids, personId) },
                            onBulkReschedule = { ids, preset -> viewModel.bulkRescheduleTasks(ids, preset) },
                            onRenameTask = { id, title -> viewModel.renameTask(id, title) },
                            onAddComment = { taskId, body -> viewModel.addComment(taskId, body) },
                            peopleEnabled = peopleFeatureEnabled,
                            tagsEnabled = tagsFeatureEnabled,
                            projectsEnabled = projectsFeatureEnabled,
                            taskRowDensity = taskRowDensity
                        )
                    }
                }
            }
        }
    }

    if (showCommandPalette) {
        CommandPaletteDialog(
            tasks = tasks,
            recentTasks = recentTasks,
            projectsEnabled = projectsFeatureEnabled,
            peopleEnabled = peopleFeatureEnabled,
            tagsEnabled = tagsFeatureEnabled,
            savedSmartFilterSets = savedSmartFilterSets,
            onDismiss = { showCommandPalette = false },
            onNewTask = {
                showCommandPalette = false
                activeSheet = MainSheetType.NewTask
            },
            onSearch = {
                showCommandPalette = false
                onNavigateToSearch()
            },
            onSettings = {
                showCommandPalette = false
                onNavigateToSettings()
            },
            onAnalytics = {
                showCommandPalette = false
                onNavigateToAnalytics()
            },
            onNextDays = {
                showCommandPalette = false
                onNavigateToNextDays()
            },
            onSavedSearch = { filters ->
                showCommandPalette = false
                onNavigateToSavedSearch(filters)
            },
            onSelectTab = { tab ->
                selectedTab = tab
                showCommandPalette = false
            },
            onOpenTask = { taskId ->
                showCommandPalette = false
                onNavigateToTaskDetail(taskId)
            }
        )
    }

    // Modal sheet routing
    if (activeSheet != MainSheetType.None) {
        if (activeSheet == MainSheetType.NewTask) {
            val requestDismissNewTask = {
                if (newTaskHasDraft) showDiscardNewTaskDialog = true
                else activeSheet = MainSheetType.None
            }
            androidx.compose.ui.window.Dialog(
                onDismissRequest = requestDismissNewTask,
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                NewTaskSheet(
                    lists = lists,
                    projects = activeProjects,
                    people = activePeople,
                    tags = tags,
                    tasks = tasks,
                    onAddTask = { draft ->
                        viewModel.addTask(draft)
                        newTaskHasDraft = false
                        activeSheet = MainSheetType.None
                    },
                    onAddTaskAndContinue = { draft ->
                        viewModel.addTask(draft)
                    },
                    onGoToExistingTask = { id ->
                        newTaskHasDraft = false
                        activeSheet = MainSheetType.None
                        onNavigateToTaskDetail(id)
                    },
                    autoAssignToMe = autoAssignToMe,
                onCreateTag = { id, name, color ->
                        viewModel.upsertTag(Tag(id = id, name = name, color = color))
                    },
                    onCreatePerson = { id, name, color ->
                        viewModel.upsertPerson(
                            Person(id = id, name = name, initials = initialsFor(name), color = color, isMe = false)
                        )
                    },
                    onDismiss = requestDismissNewTask,
                    initialListId = initialQuickAddListId,
                    initialDueDateOverride = if (selectedTab == 4) calendarSelectedDay.toString() else null,
                    projectsEnabled = projectsFeatureEnabled,
                    tagsEnabled = tagsFeatureEnabled,
                    peopleEnabled = peopleFeatureEnabled,
                    voiceLanguage = voiceLanguage,
                    defaultDueDate = defaultDueDate,
                    defaultPriority = defaultPriority,
                    onDraftStateChanged = { newTaskHasDraft = it }
                )
            }
        } else {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = MainSheetType.None },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                when (activeSheet) {
                    MainSheetType.NewProject -> ProjectEditorSheet(
                        tags = tags,
                        onSave = { name, color, icon, due, commonTagIds, defaultReminder, description, excludeFromToday ->
                            viewModel.addProject(name, color, icon, due, commonTagIds, defaultReminder, description, excludeFromToday)
                            activeSheet = MainSheetType.None
                        },
                        onDismiss = { activeSheet = MainSheetType.None }
                    )
                    MainSheetType.NewPerson -> PersonEditorSheet(
                        groups = personGroups,
                        existingNames = people.map { it.name },
                        onSave = { name, color, groupId, photoUri ->
                            viewModel.addPerson(name, color, groupId, photoUri)
                            activeSheet = MainSheetType.None
                        },
                        onCreateGroup = { id, name, color ->
                            viewModel.upsertPersonGroup(com.mj.yata.domain.model.PersonGroup(id = id, name = name, color = color))
                        },
                        onDismiss = { activeSheet = MainSheetType.None }
                    )
                    MainSheetType.NewTag -> TagEditorSheet(
                        groups = tagGroups,
                        existingNames = tags.map { it.name },
                        onSave = { name, color, groupId, hideCompletedByDefault ->
                            viewModel.addTag(name, color, groupId, hideCompletedByDefault)
                            activeSheet = MainSheetType.None
                        },
                        onCreateGroup = { id, name, color ->
                            viewModel.upsertTagGroup(com.mj.yata.domain.model.TagGroup(id = id, name = name, color = color))
                        },
                        onDismiss = { activeSheet = MainSheetType.None }
                    )
                    else -> { /* Do nothing */ }
                }
            }
        }
    }

    if (showDiscardNewTaskDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardNewTaskDialog = false },
            title = { Text(stringResource(R.string.discard_task_draft_title)) },
            text = { Text(stringResource(R.string.discard_task_draft_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardNewTaskDialog = false
                    newTaskHasDraft = false
                    activeSheet = MainSheetType.None
                }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardNewTaskDialog = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            }
        )
    }

    // Independent of activeSheet — the drawer's "New folder" + button only sets this flag,
    // so this must not be nested inside the `activeSheet != None` gate above (it used to be,
    // which meant the sheet could never actually open).
    if (isNewListSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isNewListSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            ListEditorSheet(
                onSave = { name, color, icon, excludeFromToday ->
                    viewModel.addList(name, color, icon = icon, excludeFromToday = excludeFromToday)
                    isNewListSheetOpen = false
                },
                onDismiss = { isNewListSheetOpen = false }
            )
        }
    }
}

private data class PaletteEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private fun String.smartFilterSetLabel(): String {
    val labels = mapOf(
        "OVERDUE" to "Overdue",
        "FOCUS" to "Focus mode",
        "MORNING_REVIEW" to "Morning review",
        "EVENING_REVIEW" to "Evening review",
        "STALE_TASKS" to "Stale nudges",
        "AT_RISK" to "At risk",
        "ASSIGNED_TO_ME" to "Assigned to me",
        "HIGH_PRIORITY" to "High priority",
        "FLAGGED" to "Flagged",
        "DUE_TODAY" to "Due today",
        "NO_DUE_DATE" to "No due date"
    )
    return split(",").mapNotNull { labels[it] }.ifEmpty { listOf("Saved view") }.joinToString(" + ")
}

@Composable
private fun CommandPaletteDialog(
    tasks: List<Task>,
    recentTasks: List<Task>,
    projectsEnabled: Boolean,
    peopleEnabled: Boolean,
    tagsEnabled: Boolean,
    savedSmartFilterSets: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onNewTask: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAnalytics: () -> Unit,
    onNextDays: () -> Unit,
    onSavedSearch: (String) -> Unit,
    onSelectTab: (Int) -> Unit,
    onOpenTask: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val commandEntries = listOfNotNull(
        PaletteEntry("New task", "Create a task", Icons.Default.Add, onNewTask),
        PaletteEntry("Search", "Find tasks and saved filters", Icons.Default.Search, onSearch),
        PaletteEntry("Today", "Open today's tasks", Icons.Default.Today) { onSelectTab(0) },
        if (projectsEnabled) PaletteEntry("Projects", "Open projects", Icons.Default.Layers) { onSelectTab(1) } else null,
        if (peopleEnabled) PaletteEntry("People", "Open people", Icons.Default.People) { onSelectTab(2) } else null,
        if (tagsEnabled) PaletteEntry("Tags", "Open tags", Icons.AutoMirrored.Filled.Label) { onSelectTab(3) } else null,
        PaletteEntry("Upcoming", "Open calendar and agenda", Icons.Default.CalendarViewWeek) { onSelectTab(4) },
        PaletteEntry("Next 10 Days", "Open the focused date list", Icons.Default.DateRange, onNextDays),
        // Formerly the drawer's "Tools" section. They're preset searches, so the palette — which
        // already filters by title and subtitle — is a better home than eight fixed menu rows:
        // typing "over" or "stale" reaches them without knowing where they sit.
        if (peopleEnabled) PaletteEntry("My Work", "Tasks assigned to you", Icons.Default.AssignmentInd) { onSavedSearch("ASSIGNED_TO_ME") } else null,
        PaletteEntry("Focus Mode", "High-priority and flagged work", Icons.Default.CenterFocusStrong) { onSavedSearch("FOCUS") },
        PaletteEntry("Morning Review", "Plan what's due today", Icons.Default.WbSunny) { onSavedSearch("MORNING_REVIEW") },
        PaletteEntry("Evening Review", "Wrap up and reschedule", Icons.Default.NightsStay) { onSavedSearch("EVENING_REVIEW") },
        PaletteEntry("Stale Nudges", "Tasks untouched for a while", Icons.Default.HourglassEmpty) { onSavedSearch("STALE_TASKS") },
        PaletteEntry("Task Health", "Overdue and at-risk tasks", Icons.Default.HealthAndSafety) { onSavedSearch("AT_RISK") },
        PaletteEntry("Analytics", "Open productivity insights", Icons.Default.Analytics, onAnalytics),
        PaletteEntry("Settings", "Open preferences", Icons.Default.Settings, onSettings)
    ) + savedSmartFilterSets.sorted().map { encoded ->
        // User-saved filter combos (Search screen's "Save" chip) — reachable from here too,
        // not just the drawer's "Custom Views" section, so typing part of the combo's label
        // finds it the same way a built-in preset does.
        PaletteEntry(encoded.smartFilterSetLabel(), "Saved view", Icons.Default.FilterList) { onSavedSearch(encoded) }
    }
    val filteredCommands = commandEntries.filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.subtitle.contains(query, ignoreCase = true)
    }
    val filteredRecentTasks = recentTasks.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true)
    }.take(8)
    val taskMatches = if (query.isBlank()) {
        emptyList()
    } else {
        tasks.filter { it.title.contains(query, ignoreCase = true) }.take(8)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.main_command_palette)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.main_search_actions_or_tasks)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (filteredCommands.isNotEmpty()) {
                        item { PaletteSectionLabel("Actions") }
                        items(filteredCommands, key = { "cmd_${it.title}" }) { entry ->
                            PaletteRow(entry.title, entry.subtitle, entry.icon, entry.onClick)
                        }
                    }
                    if (filteredRecentTasks.isNotEmpty()) {
                        item { PaletteSectionLabel("Recent") }
                        items(filteredRecentTasks, key = { "recent_${it.id}" }) { task ->
                            PaletteRow(task.title, task.due ?: "No due date", Icons.Default.History) {
                                onOpenTask(task.id)
                            }
                        }
                    }
                    if (taskMatches.isNotEmpty()) {
                        item { PaletteSectionLabel("Tasks") }
                        items(taskMatches, key = { "task_${it.id}" }) { task ->
                            PaletteRow(task.title, task.due ?: "No due date", Icons.Default.TaskAlt) {
                                onOpenTask(task.id)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun PaletteSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun PaletteRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    )
}

@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor
                    ?: if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                color = accentColor
                    ?: if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
            )
        }
    }
}


private data class NavIcon(val id: Int, val label: String, val outlined: ImageVector, val filled: ImageVector)

@Composable
fun CustomBottomNav(
    selectedTab: Int,
    todayBadgeCount: Int = 0,
    peopleEnabled: Boolean = true,
    tagsEnabled: Boolean = true,
    projectsEnabled: Boolean = true,
    todayEnabled: Boolean = true,
    upcomingEnabled: Boolean = true,
    onTabSelected: (Int) -> Unit
) {
    // Tab ids are fixed (0=Today, 1=Projects, 2=People, 3=Tags, 4=Upcoming) regardless of which
    // are hidden — filtering the list must not renumber the survivors, or a disabled tab in the
    // middle would shift every tab after it onto the wrong id.
    val items = listOfNotNull(
        if (todayEnabled) NavIcon(0, "Today", Icons.Outlined.Today, Icons.Filled.Today) else null,
        if (projectsEnabled) NavIcon(1, "Projects", Icons.Outlined.Layers, Icons.Filled.Layers) else null,
        if (peopleEnabled) NavIcon(2, "People", Icons.Outlined.People, Icons.Filled.People) else null,
        if (tagsEnabled) NavIcon(3, "Tags", Icons.AutoMirrored.Outlined.Label, Icons.AutoMirrored.Filled.Label) else null,
        if (upcomingEnabled) NavIcon(4, "Upcoming", Icons.Outlined.CalendarViewWeek, Icons.Filled.CalendarViewWeek) else null
    )

    // Top-only hairline (per handoff's borderTop) — a Surface `border` would ring all 4 sides,
    // drawing an unwanted line along the bottom edge too.
    val navDividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val navDividerStrokeWidth = with(androidx.compose.ui.platform.LocalDensity.current) { 1.dp.toPx() }

    // Shared sliding pill: rather than each item fading its own background in/out at its own
    // fixed spot (which reads as "pop here, pop there"), one pill element is positioned using
    // each icon's real measured coordinates and springs between them on selection change.
    var containerCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val iconPositions = remember { mutableStateMapOf<Int, androidx.compose.ui.geometry.Offset>() }
    var pillSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val isEnhancedM3 = com.mj.yata.ui.theme.LocalEnhancedM3Theming.current
    val isFloatingNav = com.mj.yata.ui.theme.LocalFloatingBottomNav.current
    val showLabels = com.mj.yata.ui.theme.LocalBottomNavLabelsEnabled.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFloatingNav) {
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = when {
                isFloatingNav -> Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(32.dp)
                    )
                isEnhancedM3 -> Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(28.dp)
                    )
                else -> Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .drawBehind {
                        drawLine(
                            color = navDividerColor,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                            strokeWidth = navDividerStrokeWidth
                        )
                    }
            },
            color = when {
                isFloatingNav && isEnhancedM3 -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f)
                isFloatingNav -> MaterialTheme.colorScheme.surfaceContainerHigh
                isEnhancedM3 -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            shadowElevation = if (isFloatingNav) 6.dp else 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isFloatingNav) Modifier else Modifier.navigationBarsPadding())
                    .padding(vertical = 4.dp)
                    .onGloballyPositioned { containerCoords = it }
            ) {
            val targetOffset = iconPositions[selectedTab]
            if (targetOffset != null && pillSize != androidx.compose.ui.unit.IntSize.Zero) {
                val animatedX by animateDpAsState(
                    targetValue = with(density) { targetOffset.x.toDp() },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "navPillX"
                )
                val animatedY by animateDpAsState(
                    targetValue = with(density) { targetOffset.y.toDp() },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "navPillY"
                )
                Box(
                    modifier = Modifier
                        .offset(x = animatedX, y = animatedY)
                        .size(with(density) { pillSize.width.toDp() }, with(density) { pillSize.height.toDp() })
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (showLabels) Modifier else Modifier.align(Alignment.Center)),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { navIcon ->
                    val isSelected = navIcon.id == selectedTab
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
                        label = "navIconTint"
                    )
                    val iconScale = remember { Animatable(1f) }
                    LaunchedEffect(isSelected) {
                        if (isSelected) {
                            iconScale.snapTo(0.8f)
                            iconScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null // the sliding pill + icon pop are the only feedback needed
                            ) { onTabSelected(navIcon.id) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(56.dp)
                                .onGloballyPositioned { coords ->
                                    pillSize = coords.size
                                    containerCoords?.let { parent ->
                                        iconPositions[navIcon.id] = parent.localPositionOf(coords, androidx.compose.ui.geometry.Offset.Zero)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) navIcon.filled else navIcon.outlined,
                                contentDescription = navIcon.label,
                                tint = iconTint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(iconScale.value)
                            )
                        }
                        if (showLabels) {
                            Text(
                                text = navIcon.label,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
}
