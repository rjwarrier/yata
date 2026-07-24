package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mj.yata.domain.model.Task
import java.time.LocalDate

/** The three stats every entity-detail hero (and Today's header) show — kept in one place so
 * "does this task match Overdue/High priority/Due today" is defined once, not once per screen. */
enum class HeroStatKind {
    OVERDUE, HIGH_PRIORITY, DUE_TODAY;

    fun matches(task: Task, today: LocalDate): Boolean {
        if (task.done) return false
        return when (this) {
            OVERDUE -> task.due != null && runCatching { LocalDate.parse(task.due) }.getOrNull()?.isBefore(today) == true
            HIGH_PRIORITY -> task.priority == "high"
            DUE_TODAY -> task.due == today.toString()
        }
    }
}

/** Shared hero header for the entity-detail screens (Person/Project/List/Tag) — a leading
 * visual (avatar or icon tile), a primary stat line, an optional name/secondary content block,
 * a trailing progress ring, and a row of overdue/high-priority/due-today stats below. Each
 * screen still computes its own counts (this widget only renders, matching how ProgressRing/
 * AssigneeStack are already used as pure presentational widgets elsewhere). Tapping a stat (if
 * [onStatClick] is supplied) is meant to toggle an in-place filter on the caller's own task list
 * — not navigate away — so [activeFilter] just controls the tapped cell's highlight. */
@Composable
fun EntityHeroSection(
    accentColor: Color,
    progress: Float,
    primaryText: String,
    overdueCount: Int,
    highPriorityCount: Int,
    dueTodayCount: Int,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    nameText: String? = null,
    secondaryContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingExtra: (@Composable () -> Unit)? = null,
    ringSize: Dp = 56.dp,
    ringStrokeWidth: Dp = 5.dp,
    activeFilter: HeroStatKind? = null,
    onStatClick: ((HeroStatKind) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(accentColor.copy(alpha = 0.16f))
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingContent()
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (nameText != null) {
                    Text(
                        text = nameText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.All
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                secondaryContent?.invoke(this)
            }
            if (trailingExtra != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingExtra()
            }
            Spacer(modifier = Modifier.width(12.dp))
            ProgressRing(
                progress = progress,
                size = ringSize,
                strokeWidth = ringStrokeWidth,
                activeColor = accentColor
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroStatCell(
                label = "Overdue",
                value = overdueCount,
                valueColor = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                active = activeFilter == HeroStatKind.OVERDUE,
                accentColor = accentColor,
                onClick = onStatClick?.let { { it(HeroStatKind.OVERDUE) } },
                modifier = Modifier.weight(1f)
            )
            HeroStatCell(
                label = "High priority",
                value = highPriorityCount,
                active = activeFilter == HeroStatKind.HIGH_PRIORITY,
                accentColor = accentColor,
                onClick = onStatClick?.let { { it(HeroStatKind.HIGH_PRIORITY) } },
                modifier = Modifier.weight(1f)
            )
            HeroStatCell(
                label = "Due today",
                value = dueTodayCount,
                active = activeFilter == HeroStatKind.DUE_TODAY,
                accentColor = accentColor,
                onClick = onStatClick?.let { { it(HeroStatKind.DUE_TODAY) } },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Public so screens without a full hero (e.g. Today's own header) can reuse the same stat-cell
 * visual for a standalone tap-to-filter stat row. */
@Composable
fun HeroStatCell(
    label: String,
    value: Int,
    accentColor: Color,
    valueColor: Color = Color.Unspecified,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .background(if (active) accentColor.copy(alpha = 0.2f) else accentColor.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun HeroStatKind.label(): String = when (this) {
    HeroStatKind.OVERDUE -> "Overdue"
    HeroStatKind.HIGH_PRIORITY -> "High priority"
    HeroStatKind.DUE_TODAY -> "Due today"
}

/** Dismissible banner shown above a task list while a [HeroStatKind] filter (tapped from
 * [EntityHeroSection] or Today's own stat row) is active. */
@Composable
fun ActiveFilterBanner(kind: HeroStatKind, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Showing: ${kind.label()}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Clear ✕",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClear)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
