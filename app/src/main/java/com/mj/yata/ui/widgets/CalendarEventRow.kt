package com.mj.yata.ui.widgets

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mj.yata.ui.theme.UiSize
import com.mj.yata.ui.theme.UiSpacing
import com.mj.yata.R
import com.mj.yata.data.calendar.CalendarEvent
import com.mj.yata.data.calendar.DeviceCalendar
import com.mj.yata.util.AppFormats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Device calendar events for [from]..[to], or nothing when [enabled] is off or the permission is
 * missing. Re-reads whenever the calendar provider reports a change, so an event added in the
 * calendar app appears without leaving the screen.
 */
@Composable
fun rememberCalendarEvents(enabled: Boolean, from: LocalDate, to: LocalDate): Map<LocalDate, List<CalendarEvent>> {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(enabled) {
        if (!enabled || !DeviceCalendar.hasPermission(context)) return@DisposableEffect onDispose {}
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { version++ }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
    val events by produceState(emptyMap<LocalDate, List<CalendarEvent>>(), enabled, from, to, version) {
        value = if (enabled) withContext(Dispatchers.IO) { DeviceCalendar.eventsByDay(context, from, to) } else emptyMap()
    }
    return events
}

/** A calendar event listed among tasks: deliberately lighter than a task card, since it can't be
 * completed or edited here, but laid out on the same grid — the color bar sits where a task's
 * checkbox does, so event and task titles share one left edge. Tapping opens it in the calendar
 * app. [horizontalPadding] is the TaskRow parameter of the same name on the surrounding list. */
@Composable
fun CalendarEventRow(event: CalendarEvent, modifier: Modifier = Modifier, horizontalPadding: Dp = 20.dp) {
    val context = LocalContext.current
    val inset = if (com.mj.yata.ui.theme.LocalTaskCardBackground.current) {
        TASK_CARD_MARGIN + TASK_CARD_CONTENT_PADDING
    } else {
        horizontalPadding
    }
    val timeLabel = if (event.allDay) {
        stringResource(R.string.calendar_event_all_day)
    } else {
        val zone = ZoneId.systemDefault()
        val formatter = AppFormats.timeFormatter()
        "${Instant.ofEpochMilli(event.begin).atZone(zone).format(formatter)} – " +
            Instant.ofEpochMilli(event.end).atZone(zone).format(formatter)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { runCatching { context.startActivity(DeviceCalendar.openIntent(event)) } }
            .padding(horizontal = inset, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(TASK_ROW_LEADING_SIZE), contentAlignment = Alignment.Center) {
            // The provider's own color for this calendar — not a theme role, since it identifies
            // which calendar the event came from, the same as it does in the calendar app.
            Box(
                Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(event.color))
            )
        }
        Spacer(Modifier.width(TASK_ROW_LEADING_GAP))
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title.ifBlank { stringResource(R.string.calendar_event_no_title) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(UiSpacing.medium))
        Icon(
            Icons.Outlined.Event,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(UiSize.iconSmall)
        )
    }
}
