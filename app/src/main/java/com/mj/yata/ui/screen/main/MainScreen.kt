package com.mj.yata.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mj.yata.domain.model.*
import com.mj.yata.ui.navigation.Screen
import com.mj.yata.ui.screen.main.tabs.*
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase
import com.mj.yata.ui.widgets.ConfettiOverlay
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.PressableScaleBox
import com.mj.yata.ui.sheets.*
import kotlinx.coroutines.launch

sealed interface MainSheetType {
    object None : MainSheetType
    object NewTask : MainSheetType
    object NewProject : MainSheetType
    object NewPerson : MainSheetType
    object NewTag : MainSheetType
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    navController: NavController,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    onNavigateToProjectDetail: (String) -> Unit,
    onNavigateToPersonDetail: (String) -> Unit,
    onNavigateToTagDetail: (String) -> Unit,
    onNavigateToListDetail: (String) -> Unit,
    initialTab: Int = 0
) {
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Main tabs state: 0=Today, 1=Projects, 2=People, 3=Tags, 4=Upcoming (Week/Month toggle inside)
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var calendarSelectedDay by remember { mutableStateOf(java.time.LocalDate.now()) }

    // Confetti trigger
    var celebrateTrigger by remember { mutableIntStateOf(0) }

    // Sheet states
    var activeSheet by remember { mutableStateOf<MainSheetType>(MainSheetType.None) }
    var isNewListSheetOpen by remember { mutableStateOf(false) }

    // Database updates flows
    val tasks by viewModel.tasks.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val tagGroups by viewModel.tagGroups.collectAsState()
    val personGroups by viewModel.personGroups.collectAsState()

    // Preferences
    val userName by viewModel.userName.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val startOfWeekSunday by viewModel.startOfWeekSunday.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()

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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Profile Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        PersonAvatar(
                            initials = userName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase(),
                            accentKey = "accentC",
                            size = 44.dp
                        )
                        Column {
                            Text(
                                text = userName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Starred projects, folders, tags & people (computed once per data change, used below)
                    val starredProjects = remember(projects) { projects.filter { it.starred } }
                    val starredLists = remember(lists) { lists.filter { it.starred } }
                    val starredTags = remember(tags) { tags.filter { it.starred } }
                    val starredPeople = remember(people) { people.filter { it.starred } }

                    // Navigation Links
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                                DrawerItem("Tags", Icons.Default.Label, selectedTab == 3) {
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

                        // Starred projects, folders, tags & people section
                        val visibleStarredProjects = if (projectsFeatureEnabled) starredProjects else emptyList()
                        val visibleStarredTags = if (tagsFeatureEnabled) starredTags else emptyList()
                        val visibleStarredPeople = if (peopleFeatureEnabled) starredPeople else emptyList()
                        if (visibleStarredProjects.isNotEmpty() || starredLists.isNotEmpty() || visibleStarredTags.isNotEmpty() || visibleStarredPeople.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "STARRED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            items(visibleStarredProjects, key = { "starred_pr_${it.id}" }) { project ->
                                DrawerItem(project.name, Icons.Default.Layers, false) {
                                    onNavigateToProjectDetail(project.id)
                                    scope.launch { drawerState.close() }
                                }
                            }
                            items(starredLists, key = { "starred_l_${it.id}" }) { list ->
                                DrawerItem(list.name, Icons.Default.Folder, false) {
                                    onNavigateToListDetail(list.id)
                                    scope.launch { drawerState.close() }
                                }
                            }
                            items(visibleStarredTags, key = { "starred_t_${it.id}" }) { tag ->
                                DrawerItem(tag.name, Icons.Default.Label, false) {
                                    onNavigateToTagDetail(tag.id)
                                    scope.launch { drawerState.close() }
                                }
                            }
                            items(visibleStarredPeople, key = { "starred_p_${it.id}" }) { person ->
                                DrawerItem(person.name, Icons.Default.Person, false) {
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
                                    text = "FOLDERS",
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
                                        contentDescription = "New folder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        items(lists) { list ->
                            DrawerItem(list.name, Icons.Default.Folder, false) {
                                onNavigateToListDetail(list.id)
                                scope.launch { drawerState.close() }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Settings link
                    DrawerItem("Settings", Icons.Default.Settings, false) {
                        onNavigateToSettings()
                        scope.launch { drawerState.close() }
                    }
                }
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                val todayRemainingCount by viewModel.todayRemainingCount.collectAsState()
                CustomBottomNav(
                    selectedTab = selectedTab,
                    todayBadgeCount = todayRemainingCount,
                    peopleEnabled = peopleFeatureEnabled,
                    tagsEnabled = tagsFeatureEnabled,
                    projectsEnabled = projectsFeatureEnabled
                ) {
                    selectedTab = it
                }
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
                    visible = fabTarget != null,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    val (fabLabel, sheetType) = fabTarget ?: ("New task" to MainSheetType.NewTask)
                    PressableScaleBox(
                        onClick = {
                            activeSheet = sheetType
                        }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(fabLabel, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onProfileClick = onNavigateToSettings,
                            onTaskClick = onNavigateToTaskDetail,
                            onToggleDone = { viewModel.toggleTaskDone(it) { celebrateTrigger++ } },
                            onBulkComplete = { viewModel.bulkCompleteTasks(it) },
                            onBulkDelete = { viewModel.bulkDeleteTasks(it) },
                            onBulkAddTag = { ids, tagId -> viewModel.bulkAddTag(ids, tagId) },
                            onBulkSetProject = { ids, projectId -> viewModel.bulkSetProject(ids, projectId) },
                            onBulkSetList = { ids, listId -> viewModel.bulkSetList(ids, listId) },
                            onBulkDuplicate = { viewModel.bulkDuplicateTasks(it) },
                            peopleEnabled = peopleFeatureEnabled,
                            tagsEnabled = tagsFeatureEnabled,
                            projectsEnabled = projectsFeatureEnabled
                        )
                        1 -> ProjectsTab(
                            projects = projects,
                            lists = lists,
                            tasks = tasks,
                            people = people,
                            userName = userName,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onProfileClick = onNavigateToSettings,
                            onProjectClick = onNavigateToProjectDetail,
                            onNewProjectClick = { activeSheet = MainSheetType.NewProject },
                            onToggleProjectStar = { viewModel.toggleProjectStarred(it) }
                        )
                        2 -> PeopleTab(
                            people = people,
                            personGroups = personGroups,
                            tasks = tasks,
                            userName = userName,
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
                            onDeleteGroup = { viewModel.deletePersonGroup(it) }
                        )
                        3 -> TagsTab(
                            tags = tags,
                            tagGroups = tagGroups,
                            tasks = tasks,
                            lists = lists,
                            projects = projects,
                            userName = userName,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onProfileClick = onNavigateToSettings,
                            onTagClick = onNavigateToTagDetail,
                            onNewTagClick = { activeSheet = MainSheetType.NewTag },
                            onToggleStar = { viewModel.toggleTagStarred(it) },
                            onDeleteGroup = { viewModel.deleteTagGroup(it) }
                        )
                        4 -> UpcomingTab(
                            tasks = tasks,
                            lists = lists,
                            projects = projects,
                            people = people,
                            tags = tags,
                            userName = userName,
                            selectedDay = calendarSelectedDay,
                            onSelectedDayChange = { calendarSelectedDay = it },
                            startOfWeekSunday = startOfWeekSunday,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onSearchClick = onNavigateToSearch,
                            onProfileClick = onNavigateToSettings,
                            onTaskClick = onNavigateToTaskDetail,
                            onToggleDone = { viewModel.toggleTaskDone(it) { celebrateTrigger++ } },
                            onBulkComplete = { viewModel.bulkCompleteTasks(it) },
                            onBulkDelete = { viewModel.bulkDeleteTasks(it) },
                            onBulkAddTag = { ids, tagId -> viewModel.bulkAddTag(ids, tagId) },
                            onBulkSetProject = { ids, projectId -> viewModel.bulkSetProject(ids, projectId) },
                            onBulkSetList = { ids, listId -> viewModel.bulkSetList(ids, listId) },
                            onBulkDuplicate = { viewModel.bulkDuplicateTasks(it) },
                            peopleEnabled = peopleFeatureEnabled,
                            tagsEnabled = tagsFeatureEnabled,
                            projectsEnabled = projectsFeatureEnabled
                        )
                    }
                }

            }
        }
    }

    // Modal sheet routing
    if (activeSheet != MainSheetType.None) {
        if (activeSheet == MainSheetType.NewTask) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { activeSheet = MainSheetType.None },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                NewTaskSheet(
                    lists = lists,
                    projects = projects,
                    people = people,
                    tags = tags,
                    onAddTask = { title, listId, priority, assignees, taskTags, rec, due, time, reminder, section, taskProjectId ->
                        viewModel.addTask(title, listId, priority, assignees, taskTags, rec, due = due, time = time, reminder = reminder, section = section, projectId = taskProjectId)
                        activeSheet = MainSheetType.None
                    },
                    onCreateTag = { id, name, color ->
                        viewModel.upsertTag(Tag(id = id, name = name, color = color))
                    },
                    onCreatePerson = { id, name, color ->
                        viewModel.upsertPerson(
                            Person(id = id, name = name, initials = initialsFor(name), color = color, isMe = false)
                        )
                    },
                    onDismiss = { activeSheet = MainSheetType.None },
                    initialDueDateOverride = if (selectedTab == 4) calendarSelectedDay.toString() else null,
                    projectsEnabled = projectsFeatureEnabled,
                    tagsEnabled = tagsFeatureEnabled,
                    peopleEnabled = peopleFeatureEnabled
                )
            }
        } else {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = MainSheetType.None },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ) {
                when (activeSheet) {
                    MainSheetType.NewProject -> ProjectEditorSheet(
                        tags = tags,
                        onSave = { name, color, icon, due, commonTagIds, defaultReminder ->
                            viewModel.addProject(name, color, icon, due, commonTagIds, defaultReminder)
                            activeSheet = MainSheetType.None
                        },
                        onDismiss = { activeSheet = MainSheetType.None }
                    )
                    MainSheetType.NewPerson -> PersonEditorSheet(
                        groups = personGroups,
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
                onSave = { name, color, icon ->
                    viewModel.addList(name, color, icon = icon)
                    isNewListSheetOpen = false
                },
                onDismiss = { isNewListSheetOpen = false }
            )
        }
    }
}

@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
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
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
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
    onTabSelected: (Int) -> Unit
) {
    // Tab ids are fixed (0=Today, 1=Projects, 2=People, 3=Tags, 4=Upcoming) regardless of which
    // are hidden — filtering the list must not renumber the survivors, or a disabled tab in the
    // middle would shift every tab after it onto the wrong id.
    val items = listOfNotNull(
        NavIcon(0, "Today", Icons.Outlined.Today, Icons.Filled.Today),
        if (projectsEnabled) NavIcon(1, "Projects", Icons.Outlined.Layers, Icons.Filled.Layers) else null,
        if (peopleEnabled) NavIcon(2, "People", Icons.Outlined.People, Icons.Filled.People) else null,
        if (tagsEnabled) NavIcon(3, "Tags", Icons.Outlined.Label, Icons.Filled.Label) else null,
        NavIcon(4, "Upcoming", Icons.Outlined.CalendarViewWeek, Icons.Filled.CalendarViewWeek)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { navIcon ->
                val isSelected = navIcon.id == selectedTab
                val indicatorColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    animationSpec = tween(YataDur.micro, easing = YataEase.emphasized),
                    label = "navIndicatorColor"
                )
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
                        .clickable { onTabSelected(navIcon.id) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .width(56.dp)
                            .clip(CircleShape)
                            .background(indicatorColor),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (navIcon.id == 0 && todayBadgeCount > 0) {
                                    Badge { Text(if (todayBadgeCount > 99) "99+" else todayBadgeCount.toString()) }
                                }
                            }
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
                    }
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

