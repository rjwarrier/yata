package com.mj.yata.ui.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mj.yata.R

private data class FloatingNavItem(
    val id: Int,
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector
)

private val FloatingNavTabHeight = 64.dp
private val FloatingNavCollapsedTabWidth = 64.dp
private val FloatingNavCapsuleHeight = 72.dp
private val FloatingNavCapsuleInternalPadding = 4.dp
private val FloatingNavOuterMargin = 16.dp
private val FloatingNavIconSize = 24.dp

/**
 * Centered floating capsule bottom navigation bar — the "Floating bottom nav" Settings
 * appearance option's actual presentation, modeled on the sibling Vayana app's floating nav bar
 * (see `docs/FLOATING_NAV_IMPLEMENTATION.md` in that repo). Unlike Vayana's dual-spring elastic
 * -stretch indicator, this uses a single spring per animated value — visually close enough at
 * normal interaction speed without the extra geometry. Also skips Vayana's companion buttons
 * entirely: YATA's per-tab create action already lives in its own always-visible FAB.
 *
 * Call sites build their own enabled-tab list the same way [com.mj.yata.ui.screen.main.CustomBottomNav]
 * does — tab ids (0=Today,1=Projects,2=People,3=Tags,4=Upcoming) stay fixed regardless of which
 * are hidden by a feature flag, so a hidden tab never renumbers the ones after it.
 */
@Composable
fun FloatingCapsuleBottomNav(
    selectedTab: Int,
    todayBadgeCount: Int = 0,
    peopleEnabled: Boolean = true,
    tagsEnabled: Boolean = true,
    projectsEnabled: Boolean = true,
    todayEnabled: Boolean = true,
    upcomingEnabled: Boolean = true,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOfNotNull(
        if (todayEnabled) FloatingNavItem(0, stringResource(R.string.tab_today), Icons.Outlined.Today, Icons.Filled.Today) else null,
        if (projectsEnabled) FloatingNavItem(1, stringResource(R.string.tab_projects), Icons.Outlined.Layers, Icons.Filled.Layers) else null,
        if (peopleEnabled) FloatingNavItem(2, stringResource(R.string.tab_people), Icons.Outlined.People, Icons.Filled.People) else null,
        if (tagsEnabled) FloatingNavItem(3, stringResource(R.string.tab_tags), Icons.AutoMirrored.Outlined.Label, Icons.AutoMirrored.Filled.Label) else null,
        if (upcomingEnabled) FloatingNavItem(4, stringResource(R.string.tab_upcoming), Icons.Outlined.CalendarViewWeek, Icons.Filled.CalendarViewWeek) else null
    )
    if (items.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = FloatingNavOuterMargin, vertical = FloatingNavOuterMargin),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current

        // Small squash on every selection change (section 6) — capsule briefly shortens and
        // widens, then springs back, giving the whole bar a touch of life beyond the sliding pill.
        val squashScaleY = remember { Animatable(1f) }
        LaunchedEffect(selectedTab) {
            squashScaleY.snapTo(0.975f)
            squashScaleY.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }

        var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val tabPositions = remember { mutableStateMapOf<Int, Offset>() }
        val tabSizes = remember { mutableStateMapOf<Int, IntSize>() }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            shadowElevation = 6.dp,
            modifier = Modifier
                .height(FloatingNavCapsuleHeight)
                .graphicsLayer {
                    scaleY = squashScaleY.value
                    scaleX = 1f + (1f - squashScaleY.value) * 0.35f
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            Box(
                modifier = Modifier
                    .padding(
                        horizontal = FloatingNavCapsuleInternalPadding,
                        vertical = FloatingNavCapsuleInternalPadding
                    )
                    .onGloballyPositioned { containerCoords = it },
                contentAlignment = Alignment.CenterStart
            ) {
                // Single sliding pill tracking the real measured position/size of the selected
                // tab — same technique as CustomBottomNav's own pill.
                val indicatorOffset = tabPositions[selectedTab]
                val indicatorSize = tabSizes[selectedTab]
                if (indicatorOffset != null && indicatorSize != null) {
                    val animatedX by animateDpAsState(
                        targetValue = with(density) { indicatorOffset.x.toDp() },
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                        label = "floatingNavIndicatorX"
                    )
                    val animatedWidth by animateDpAsState(
                        targetValue = with(density) { indicatorSize.width.toDp() },
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
                        label = "floatingNavIndicatorWidth"
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = animatedX)
                            .height(FloatingNavTabHeight - FloatingNavCapsuleInternalPadding * 2)
                            .width(animatedWidth)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    items.forEach { item ->
                        val isSelected = item.id == selectedTab
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val pressScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.93f else 1f,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
                            label = "floatingNavPressScale"
                        )
                        val contentTint by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 180),
                            label = "floatingNavTint"
                        )

                        Box(
                            modifier = Modifier
                                .height(FloatingNavTabHeight)
                                .width(FloatingNavCollapsedTabWidth)
                                .graphicsLayer {
                                    scaleX = pressScale
                                    scaleY = pressScale
                                }
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null // the sliding pill + press scale are feedback enough
                                ) { onTabSelected(item.id) }
                                .onGloballyPositioned { coords ->
                                    tabSizes[item.id] = coords.size
                                    containerCoords?.let { parent ->
                                        tabPositions[item.id] = parent.localPositionOf(coords, Offset.Zero)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val icon: @Composable () -> Unit = {
                                    Icon(
                                        imageVector = if (isSelected) item.filled else item.outlined,
                                        contentDescription = item.label,
                                        tint = contentTint,
                                        modifier = Modifier.size(FloatingNavIconSize)
                                    )
                                }
                                if (item.id == 0 && todayBadgeCount > 0) {
                                    BadgedBox(badge = { Badge { Text(todayBadgeCount.coerceAtMost(99).toString()) } }) {
                                        icon()
                                    }
                                } else {
                                    icon()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
