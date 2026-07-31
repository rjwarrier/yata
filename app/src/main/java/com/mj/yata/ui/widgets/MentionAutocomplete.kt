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
import com.mj.yata.domain.model.Project
import com.mj.yata.domain.model.Tag
import com.mj.yata.domain.model.YataList
import com.mj.yata.domain.model.activeLists
import com.mj.yata.domain.model.activeProjects
import com.mj.yata.ui.theme.LocalYataAccents

/**
 * Shared inline-mention autocomplete — originally built for NewTaskSheet's title field, extracted
 * here so any other free-text task-title field (e.g. TaskDetailScreen's rename-in-place field) can
 * offer the same convention instead of silently ignoring the triggers.
 *
 * Four entity types, one trigger each:
 *
 * | trigger | entity  |
 * |---------|---------|
 * | `#`     | tag     |
 * | `@`     | person  |
 * | `+`     | project |
 * | `=`     | list    |
 *
 * Projects and lists were reachable only through the natural-language parser's `project Foo` /
 * `list Bar` phrasing, which has no autocomplete and no feedback until the parse lands — so two of
 * the four entity types had a fast path and two did not.
 *
 * The characters are picked to stay clear of everything [com.mj.yata.util.NaturalLanguageParser]
 * already claims: `/` is a date separator, `!` is priority. `+` appears in that parser only as a
 * mid-phrase weekday separator ("every mon + wed"), never leading a word, and `=` is unused there
 * entirely.
 */
internal data class MentionToken(val trigger: Char, val query: String, val startIndex: Int)

internal const val TRIGGER_TAG = '#'
internal const val TRIGGER_PERSON = '@'
internal const val TRIGGER_PROJECT = '+'
internal const val TRIGGER_LIST = '='

private val MENTION_TRIGGERS = charArrayOf(TRIGGER_TAG, TRIGGER_PERSON, TRIGGER_PROJECT, TRIGGER_LIST)

/** Finds an in-progress mention token ending at the cursor, if any. */
internal fun detectMentionToken(text: String, cursor: Int): MentionToken? {
    if (cursor <= 0 || cursor > text.length) return null
    var i = cursor - 1
    while (i >= 0) {
        val c = text[i]
        if (c in MENTION_TRIGGERS) {
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
    onCreatePerson: (String) -> Unit,
    // Optional so a caller that only wants #/@ — the rename field on Task Detail, which has no
    // project or list picker to put the result in — needs no changes and simply shows nothing for
    // the other two triggers.
    projects: List<Project> = emptyList(),
    lists: List<YataList> = emptyList(),
    onSelectProject: ((Project) -> Unit)? = null,
    onSelectList: ((YataList) -> Unit)? = null
) {
    val accents = LocalYataAccents.current
    val query = mention.query
    // Nothing to offer and nowhere to put a choice: drawing an empty panel over the keyboard would
    // be worse than leaving the typed character as plain text, which is what happens anyway.
    if (mention.trigger == TRIGGER_PROJECT && onSelectProject == null) return
    if (mention.trigger == TRIGGER_LIST && onSelectList == null) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (mention.trigger == TRIGGER_PROJECT) {
                val matches = projects.activeProjects()
                    .filter { it.name.contains(query, ignoreCase = true) }
                    .sortedBy { it.name.lowercase() }
                if (matches.isEmpty() && query.isBlank()) {
                    MentionPanelHint("Type to search projects")
                }
                matches.take(5).forEach { project ->
                    val color = accents.getAccent(project.color)
                    MentionRow(
                        label = project.name,
                        onClick = { onSelectProject?.invoke(project) },
                        leading = {
                            Icon(
                                imageVector = iconVectorFor(project.icon),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                if (matches.isEmpty() && query.isNotBlank()) {
                    // No "create" row, unlike tags and people. A project is a heavier thing —
                    // colour, icon, description, common tags — and conjuring one from a name typed
                    // mid-sentence would make a half-configured project too easy to create by
                    // accident. The editor sheet is one tap away on the chip row.
                    MentionPanelHint("No project matches \"$query\"")
                }
            } else if (mention.trigger == TRIGGER_LIST) {
                val matches = lists.activeLists()
                    .filter { it.name.contains(query, ignoreCase = true) }
                    .sortedBy { it.name.lowercase() }
                if (matches.isEmpty() && query.isBlank()) {
                    MentionPanelHint("Type to search lists")
                }
                matches.take(5).forEach { list ->
                    val color = accents.getAccent(list.color)
                    MentionRow(
                        label = list.name,
                        onClick = { onSelectList?.invoke(list) },
                        leading = {
                            Icon(
                                imageVector = iconVectorFor(list.icon),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                if (matches.isEmpty() && query.isNotBlank()) {
                    MentionPanelHint("No list matches \"$query\"")
                }
            } else if (mention.trigger == TRIGGER_TAG) {
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
                            com.mj.yata.ui.widgets.PersonAvatar(
                                initials = person.initials,
                                accentKey = person.color,
                                size = 20.dp,
                                photoUri = person.photoUri
                            )
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
