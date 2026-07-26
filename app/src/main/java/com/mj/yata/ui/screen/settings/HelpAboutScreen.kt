package com.mj.yata.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.BuildConfig
import com.mj.yata.R
import com.mj.yata.ui.screen.main.CustomBottomNav
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.BodoniModaFamily

private data class HelpSection(
    val title: String,
    val description: String,
    val bullets: List<String>,
    val icon: ImageVector
)

private val helpSections = listOf(
    HelpSection(
        title = "Today",
        description = "Your day view combines overdue work and tasks due today.",
        bullets = listOf(
            "The progress ring counts tasks that were pending at the start of today.",
            "Complete, snooze, comment, edit, or delete from each row.",
            "Use the eye control to hide completed tasks when you want a cleaner list."
        ),
        icon = Icons.Default.Today
    ),
    HelpSection(
        title = "Upcoming & Calendar",
        description = "Plan across the next week or switch to a full month calendar.",
        bullets = listOf(
            "The date strip starts from today and shows task dots by list color.",
            "Calendar mode lets you jump across months without leaving the agenda.",
            "Filters narrow the agenda to assigned, delegated, or high priority work."
        ),
        icon = Icons.Default.CalendarMonth
    ),
    HelpSection(
        title = "Quick Add",
        description = "Create tasks quickly with natural language.",
        bullets = listOf(
            "Try text like \"call Priya tomorrow 3pm high priority\".",
            "YATA can detect due dates, times, priority, and list names.",
            "Quick Add also works from shortcuts, widgets, voice input, and share sheets."
        ),
        icon = Icons.Default.PostAdd
    ),
    HelpSection(
        title = "Projects & Lists",
        description = "Use projects for larger outcomes and lists for reusable buckets.",
        bullets = listOf(
            "Star important projects or lists to keep them in the drawer.",
            "Archive old containers without deleting their data.",
            "Exclude backlog-style containers from Today when their tasks should stay out of the daily view."
        ),
        icon = Icons.Default.ViewAgenda
    ),
    HelpSection(
        title = "People",
        description = "Assign tasks and track delegated work.",
        bullets = listOf(
            "Mark one person as you for assigned-to-me filtering.",
            "Person detail screens show open and completed work for that person.",
            "Team overdue widgets summarize who needs attention."
        ),
        icon = Icons.Default.Groups
    ),
    HelpSection(
        title = "Tags",
        description = "Add flexible labels that cut across lists and projects.",
        bullets = listOf(
            "Group tags for cleaner browsing.",
            "Use tag detail screens to review everything with that label.",
            "Tags can have default hide-completed behavior for focused views."
        ),
        icon = Icons.Default.Label
    ),
    HelpSection(
        title = "Analytics",
        description = "Review progress patterns and workload health.",
        bullets = listOf(
            "Track completions, streaks, on-time rate, and aging buckets.",
            "Breakdowns show work by project, person, and tag.",
            "Use Analytics when you want to rebalance work instead of just clear tasks."
        ),
        icon = Icons.Default.Analytics
    ),
    HelpSection(
        title = "Reminders",
        description = "Set per-task alerts that survive device reboots.",
        bullets = listOf(
            "Reminders use Android alarms and are rescheduled after reboot.",
            "Notification actions let you complete or snooze without opening the app.",
            "Exact alarm and battery settings affect how reliably alerts arrive."
        ),
        icon = Icons.Default.Notifications
    ),
    HelpSection(
        title = "Widgets",
        description = "Keep YATA visible outside the phone app.",
        bullets = listOf(
            "Home widgets cover today, upcoming, progress, quick add, team overdue, and one pinned list.",
            "Widget appearance can use dynamic color, opacity, corner radius, labels, and accents.",
            "A Quick Settings tile adds a task straight from the notification shade."
        ),
        icon = Icons.Default.Widgets
    ),
    HelpSection(
        title = "Backup & Export",
        description = "Protect or move your data when you need to.",
        bullets = listOf(
            "Cloud backup uses Google Drive app data storage when enabled.",
            "File backup and restore use JSON for the full YATA dataset.",
            "Calendar export creates an .ics file, and Markdown export is useful for sharing."
        ),
        icon = Icons.Default.Backup
    ),
    HelpSection(
        title = "Trash",
        description = "Deleted tasks are recoverable before they are permanently removed.",
        bullets = listOf(
            "Task deletes are soft deletes with Undo where available.",
            "Trash lets you restore or permanently delete individual tasks.",
            "Old trash is purged after 30 days."
        ),
        icon = Icons.Default.RestoreFromTrash
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpAboutScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayBadgeCount = viewModel.todayRemainingCount.collectAsStateWithLifecycle().value
    val peopleFeatureEnabled = viewModel.peopleFeatureEnabled.collectAsStateWithLifecycle().value
    val tagsFeatureEnabled = viewModel.tagsFeatureEnabled.collectAsStateWithLifecycle().value
    val projectsFeatureEnabled = viewModel.projectsFeatureEnabled.collectAsStateWithLifecycle().value
    val todayTabEnabled = viewModel.todayTabEnabled.collectAsStateWithLifecycle().value
    val upcomingTabEnabled = viewModel.upcomingTabEnabled.collectAsStateWithLifecycle().value
    val demoModeEnabled by viewModel.demoModeEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            CustomBottomNav(
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
                    Text(stringResource(R.string.help_about_help_about),
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "HELP",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(helpSections) { section ->
                HelpSectionCard(section = section)
            }

            item {
                Text(
                    text = "ABOUT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    viewModel.toggleDemoMode()
                                    Toast.makeText(
                                        context,
                                        if (demoModeEnabled) "Demo mode off — showing your real data" else "Demo mode on — showing sample data for screenshots",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.rj_logo_mark),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                                modifier = Modifier.size(width = 44.dp, height = 29.dp)
                            )
                        }
                        if (demoModeEnabled) {
                            Text(
                                text = "DEMO MODE — tap logo to switch back",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "yata",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = BodoniModaFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "yet another todo app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "From the Labs of RJ",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Made in India",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpSectionCard(section: HelpSection) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = section.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.bullets.forEach { bullet ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "-",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = bullet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
