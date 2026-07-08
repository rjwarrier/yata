package com.mj.yata.ui.screen.welcome

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.ui.theme.LocalYataAccents
import kotlinx.coroutines.launch

private data class WelcomePage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private val pages = listOf(
    WelcomePage(
        icon = Icons.Default.TaskAlt,
        title = "Welcome to YATA",
        description = "Yet Another Task App — organize your day, delegate to your team, and see everything at a glance. A quick tour of the basics."
    ),
    WelcomePage(
        icon = Icons.Default.Layers,
        title = "Projects & Lists",
        description = "Projects group related tasks and track their combined progress (e.g. a client engagement). Lists are simpler flat groupings for anything that doesn't need project-level tracking."
    ),
    WelcomePage(
        icon = Icons.Default.People,
        title = "People & Delegation",
        description = "Assign tasks to yourself or teammates. The People tab shows who's carrying how much work, including overdue counts — so you can see at a glance who needs help."
    ),
    WelcomePage(
        icon = Icons.Default.Label,
        title = "Tags",
        description = "Flexible labels that cut across projects and lists — group by category, client type, or anything else that doesn't map to a single project."
    ),
    WelcomePage(
        icon = Icons.Default.CalendarMonth,
        title = "Today & Upcoming",
        description = "Today shows what's due now or overdue. Upcoming gives you a week/month view of what's coming, so nothing sneaks up on you."
    ),
    WelcomePage(
        icon = Icons.Default.Analytics,
        title = "Analytics",
        description = "Completion streaks, overdue aging, on-time rate, and per-person workload share — the numbers behind how your team's actually doing."
    )
)

/** Shown automatically on first launch after install, and replayable any time from
 * Settings → About → "Show welcome tour". [onFinish] marks it seen (a no-op if already
 * seen, e.g. when replayed) and returns to whatever screen launched it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val accents = LocalYataAccents.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text("Skip")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                val item = pages[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(accents.accentA.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = accents.accentA,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                pages.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) accents.accentA
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
                    .height(52.dp)
            ) {
                Text(if (pagerState.currentPage < pages.lastIndex) "Next" else "Get Started")
            }
        }
    }
}
