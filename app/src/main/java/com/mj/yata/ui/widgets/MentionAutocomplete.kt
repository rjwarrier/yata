package com.mj.yata.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.domain.model.Person
import com.mj.yata.domain.model.Tag
import com.mj.yata.ui.theme.LocalYataAccents

/**
 * Shared `#tag` / `@person` inline-mention autocomplete — originally built for NewTaskSheet's
 * title field, extracted here so any other free-text task-title field (e.g. TaskDetailScreen's
 * rename-in-place field) can offer the same convention instead of silently ignoring `#`/`@`.
 */
internal data class MentionToken(val trigger: Char, val query: String, val startIndex: Int)

/** Finds an in-progress `#tag` or `@person` token ending at the cursor, if any. */
internal fun detectMentionToken(text: String, cursor: Int): MentionToken? {
    if (cursor <= 0 || cursor > text.length) return null
    var i = cursor - 1
    while (i >= 0) {
        val c = text[i]
        if (c == '#' || c == '@') {
            val precededByBoundary = i == 0 || text[i - 1].isWhitespace()
            if (!precededByBoundary) return null
            val query = text.substring(i + 1, cursor)
            if (query.any { it.isWhitespace() }) return null
            return MentionToken(c, query, i)
        }
        if (c.isWhitespace()) return null
        i--
    }
    return null
}

/** Removes the active mention token (trigger char + query) from the field, collapsing the gap. */
internal fun consumeMentionToken(value: TextFieldValue, mention: MentionToken): TextFieldValue {
    val before = value.text.substring(0, mention.startIndex)
    val after = value.text.substring(value.selection.end)
    return TextFieldValue(before + after, TextRange(before.length))
}

@Composable
internal fun MentionSuggestions(
    mention: MentionToken,
    tags: List<Tag>,
    people: List<Person>,
    onSelectTag: (Tag) -> Unit,
    onSelectPerson: (Person) -> Unit,
    onCreateTag: (String) -> Unit,
    onCreatePerson: (String) -> Unit
) {
    val accents = LocalYataAccents.current
    val query = mention.query
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (mention.trigger == '#') {
                val matches = tags.filter { it.name.contains(query, ignoreCase = true) }.sortedBy { it.name.lowercase() }
                if (matches.isEmpty() && query.isBlank()) {
                    MentionPanelHint("Type to search or create a tag")
                }
                matches.take(5).forEach { tag ->
                    val color = accents.getAccent(tag.color)
                    MentionRow(
                        label = tag.name,
                        onClick = { onSelectTag(tag) },
                        leading = { Box(modifier = Modifier.size(8.dp).background(color, CircleShape)) }
                    )
                }
                if (query.isNotBlank() && matches.none { it.name.equals(query, ignoreCase = true) }) {
                    MentionRow(
                        label = "Create tag \"$query\"",
                        onClick = { onCreateTag(query) },
                        leading = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                val matches = people.filter { it.name.contains(query, ignoreCase = true) }.sortedBy { it.name.lowercase() }
                if (matches.isEmpty() && query.isBlank()) {
                    MentionPanelHint("Type to search or create a person")
                }
                matches.take(5).forEach { person ->
                    MentionRow(
                        label = if (person.isMe) "You" else person.name,
                        onClick = { onSelectPerson(person) },
                        leading = {
                            Box(
                                modifier = Modifier.size(20.dp).background(accents.getAccent(person.color), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(person.initials, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
                if (query.isNotBlank() && matches.none { it.name.equals(query, ignoreCase = true) }) {
                    MentionRow(
                        label = "Create person \"$query\"",
                        onClick = { onCreatePerson(query) },
                        leading = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionRow(
    label: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        leading()
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = labelColor)
    }
}

@Composable
private fun MentionPanelHint(text: String) {
    Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
