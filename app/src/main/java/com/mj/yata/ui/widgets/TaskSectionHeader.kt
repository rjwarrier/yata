package com.mj.yata.ui.widgets

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "PENDING (3)" / "COMPLETED (2)" header shared by every task-list screen that splits tasks
 * into the two sections — callers only render this at all when the section is non-empty, and
 * skip both sections entirely while completed tasks are hidden. No horizontal padding of its
 * own since it's meant to sit inside a LazyColumn that already applies horizontal contentPadding. */
@Composable
fun TaskSectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp)
    )
}
