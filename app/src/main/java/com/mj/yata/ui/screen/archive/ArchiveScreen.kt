package com.mj.yata.ui.screen.archive

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mj.yata.R
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.YataDur
import com.mj.yata.ui.theme.YataEase

/**
 * Archived tasks — shelved but fully intact, unlike Trash. Nothing here expires or is purged;
 * the only way out is unarchiving (or deleting from the task's own detail screen).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onNavigateToTaskDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val archivedTasks by viewModel.archivedTasks.collectAsState()
    val todayBadgeCount by viewModel.todayRemainingCount.collectAsState()
    val peopleFeatureEnabled by viewModel.peopleFeatureEnabled.collectAsState()
    val tagsFeatureEnabled by viewModel.tagsFeatureEnabled.collectAsState()
    val projectsFeatureEnabled by viewModel.projectsFeatureEnabled.collectAsState()
    val todayTabEnabled by viewModel.todayTabEnabled.collectAsState()
    val upcomingTabEnabled by viewModel.upcomingTabEnabled.collectAsState()

    Scaffold(
        bottomBar = {
            com.mj.yata.ui.screen.main.CustomBottomNav(
                selectedTab = -1,
                todayBadgeCount = todayBadgeCount,
                peopleEnabled = peopleFeatureEnabled,
                tagsEnabled = tagsFeatureEnabled,
                projectsEnabled = projectsFeatureEnabled,
                todayEnabled = todayTabEnabled,
                upcomingEnabled = upcomingTabEnabled,
                onTabSelected = onNavigateToTab
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.archive_title),
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.ExtraBold,
                            fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        if (archivedTasks.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.archive_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.archive_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(archivedTasks, key = { it.id }) { task ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(placementSpec = tween(durationMillis = YataDur.sheet, easing = YataEase.emphasized)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToTaskDetail(task.id) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 2
                                )
                                task.due?.let { due ->
                                    Text(
                                        text = due,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.setTaskArchived(task.id, false) }) {
                                Icon(Icons.Default.Unarchive, contentDescription = stringResource(R.string.cd_archive_unarchive))
                            }
                        }
                    }
                }
            }
        }
    }
}
