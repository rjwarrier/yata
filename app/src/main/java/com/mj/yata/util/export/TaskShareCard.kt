package com.mj.yata.util.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.ui.theme.DarkAccents
import com.mj.yata.ui.theme.DarkColors
import com.mj.yata.ui.theme.LightAccents
import com.mj.yata.ui.theme.LightColors
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.theme.Shapes
import com.mj.yata.ui.theme.createTypography
import com.mj.yata.ui.widgets.PersonAvatar

/** Design constants below are lifted 1:1 from the reference mockups (`task-share-compact.jpg` /
 * `task-share-full.jpg`), specified as a 1080px-wide portrait canvas. [TaskShareCard] treats
 * every one of those px numbers as a dp value and relies on [captureTaskShareBitmap] pinning
 * [LocalDensity] to `targetWidthPx / 1080f` (with `fontScale` locked to 1) so the rendered bitmap
 * comes out pixel-faithful to the spec regardless of the exporting device's real display density. */
private const val ShareCanvasWidthDp = 1080

private val HorizontalPadding = 64.dp

@Composable
private fun shareCardColors(darkTheme: Boolean) =
    if (darkTheme) DarkColors to DarkAccents else LightColors to LightAccents

/**
 * Portrait "share task as image" card — the counterpart to [TaskExportCard] used specifically by
 * the IMAGE export path (PDF keeps using [TaskExportCard]'s denser report layout). Sized and
 * captured for chat-app share-sheet previews (WhatsApp/Telegram/Instagram), not for print.
 *
 * Every optional section (status pills, the due/list strip, assignees, tags, notes, comments)
 * collapses fully when it has nothing to show — no empty labels, no reserved space — so the
 * footer always sits directly under the last rendered block, and height is content-driven rather
 * than fixed.
 */
@Composable
fun TaskShareCard(
    title: String,
    done: Boolean,
    priority: String,
    flagged: Boolean,
    dueLabel: String?,
    overdue: Boolean,
    completedAtLabel: String?,
    listName: String?,
    assignees: List<ExportPersonChip>,
    tagChips: List<ExportTagChip>,
    notes: String?,
    includeNotes: Boolean,
    comments: List<ExportCommentRow>,
    includeComments: Boolean,
    sharedByName: String,
    sharedByInitials: String,
    sharedByAccentKey: String,
    sharedByPhotoUri: String?,
    sharedOnLabel: String,
    darkTheme: Boolean = false
) {
    val showNotes = includeNotes && !notes.isNullOrBlank()
    val visibleComments = if (includeComments) comments.take(3) else emptyList()
    val moreComments = if (includeComments) (comments.size - visibleComments.size).coerceAtLeast(0) else 0
    val (colorScheme, accents) = shareCardColors(darkTheme)

    CompositionLocalProvider(LocalYataAccents provides accents) {
        MaterialTheme(colorScheme = colorScheme, typography = createTypography(), shapes = Shapes) {
            Surface(color = colorScheme.surface) {
                Column(modifier = Modifier.width(ShareCanvasWidthDp.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(Brush.horizontalGradient(listOf(colorScheme.tertiary, colorScheme.primary)))
                    )

                    Column(modifier = Modifier.padding(horizontal = HorizontalPadding)) {
                        Spacer(modifier = Modifier.height(40.dp))
                        BrandRow(colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)

                        Spacer(modifier = Modifier.height(36.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 64.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 73.sp,
                                letterSpacing = (-0.015).em,
                                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = colorScheme.onSurface,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SharePill(
                                text = stringResource(if (done) R.string.export_done_badge else R.string.export_open_badge),
                                background = colorScheme.secondaryContainer,
                                textColor = colorScheme.onSecondaryContainer
                            )
                            if (priority != "none") {
                                val (bg, fg) = when (priority) {
                                    "high" -> colorScheme.error to colorScheme.onError
                                    "med" -> colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer
                                    else -> colorScheme.surfaceContainerHighest to colorScheme.onSurfaceVariant
                                }
                                SharePill(
                                    text = stringResource(R.string.export_priority_badge, priority.uppercase()),
                                    background = bg,
                                    textColor = fg
                                )
                            }
                            if (flagged) {
                                SharePill(
                                    text = stringResource(R.string.export_flagged_badge),
                                    background = colorScheme.error,
                                    textColor = colorScheme.onError
                                )
                            }
                            if (overdue && !done) {
                                SharePill(
                                    text = stringResource(R.string.export_overdue_badge),
                                    background = colorScheme.error,
                                    textColor = colorScheme.onError
                                )
                            }
                        }

                        val scheduleValue = completedAtLabel ?: dueLabel
                        if (scheduleValue != null || listName != null) {
                            Spacer(modifier = Modifier.height(28.dp))
                            DueListStrip(
                                scheduleLabel = if (completedAtLabel == null) stringResource(R.string.export_due_label) else null,
                                scheduleValue = scheduleValue,
                                listName = listName,
                                stripColor = colorScheme.surfaceContainerHigh,
                                iconTileColor = colorScheme.tertiaryContainer,
                                iconTint = colorScheme.onTertiaryContainer,
                                labelColor = colorScheme.onSurfaceVariant,
                                valueColor = colorScheme.onSurface,
                                listValueColor = colorScheme.tertiary
                            )
                        }

                        if (assignees.isNotEmpty() || tagChips.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                if (assignees.isNotEmpty()) {
                                    AssignedToBlock(
                                        assignees = assignees,
                                        blockColor = colorScheme.surfaceContainer,
                                        labelColor = colorScheme.onSurfaceVariant,
                                        nameColor = colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (tagChips.isNotEmpty()) {
                                    TagsBlock(
                                        tagChips = tagChips,
                                        blockColor = colorScheme.surfaceContainer,
                                        labelColor = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (showNotes) {
                            Spacer(modifier = Modifier.height(28.dp))
                            NotesBlock(
                                notes = notes.orEmpty(),
                                barColor = colorScheme.primary,
                                panelColor = colorScheme.surfaceContainerHigh,
                                labelColor = colorScheme.onSurfaceVariant,
                                textColor = colorScheme.onSurface
                            )
                        }

                        if (visibleComments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(28.dp))
                            CommentsBlock(
                                comments = visibleComments,
                                moreCount = moreComments,
                                labelColor = colorScheme.onSurfaceVariant,
                                bodyColor = colorScheme.onSurface,
                                metaColor = colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                    }

                    ShareFooter(
                        sharedByName = sharedByName,
                        sharedByInitials = sharedByInitials,
                        sharedByAccentKey = sharedByAccentKey,
                        sharedByPhotoUri = sharedByPhotoUri,
                        sharedOnLabel = sharedOnLabel,
                        bandColor = colorScheme.surfaceContainerHigh,
                        nameColor = colorScheme.tertiary,
                        metaColor = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandRow(squareColor: Color, glyphTint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(squareColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                tint = glyphTint,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = stringResource(R.string.export_yata_wordmark),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.22.em
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SharePill(text: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em
            ),
            color = textColor
        )
    }
}

@Composable
private fun ShareSectionLabel(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.18.em
        ),
        color = color
    )
}

@Composable
private fun DueListStrip(
    scheduleLabel: String?,
    scheduleValue: String?,
    listName: String?,
    stripColor: Color,
    iconTileColor: Color,
    iconTint: Color,
    labelColor: Color,
    valueColor: Color,
    listValueColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(stripColor)
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (scheduleValue != null) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconTileColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                if (scheduleLabel != null) {
                    ShareSectionLabel(scheduleLabel, labelColor)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = scheduleValue,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium),
                    color = valueColor
                )
            }
        }
        if (listName != null) {
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                ShareSectionLabel(stringResource(R.string.export_list_label), labelColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium),
                    color = listValueColor
                )
            }
        }
    }
}

@Composable
private fun AssignedToBlock(
    assignees: List<ExportPersonChip>,
    blockColor: Color,
    labelColor: Color,
    nameColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(blockColor)
            .padding(horizontal = 36.dp, vertical = 34.dp)
    ) {
        ShareSectionLabel(stringResource(R.string.export_assigned_to_label), labelColor)
        Spacer(modifier = Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            assignees.forEach { person ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(
                        initials = person.initials,
                        accentKey = person.accentKey,
                        size = 68.dp,
                        photoUri = person.photoUri
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 34.sp, fontWeight = FontWeight.Medium),
                        color = nameColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsBlock(
    tagChips: List<ExportTagChip>,
    blockColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(blockColor)
            .padding(horizontal = 36.dp, vertical = 34.dp)
    ) {
        ShareSectionLabel(stringResource(R.string.export_tags_label), labelColor)
        Spacer(modifier = Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tagChips.forEach { chip ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chip.color.copy(alpha = 0.16f))
                        .padding(horizontal = 26.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium),
                        color = chip.color.copy(alpha = 0.55f)
                    )
                    Text(
                        text = chip.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Medium),
                        color = chip.color
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesBlock(notes: String, barColor: Color, panelColor: Color, labelColor: Color, textColor: Color) {
    Column {
        ShareSectionLabel(stringResource(R.string.export_notes_heading), labelColor)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight()
                    .background(barColor)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp))
                    .background(panelColor)
                    .padding(horizontal = 32.dp, vertical = 28.dp)
            ) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 30.sp, lineHeight = 43.5.sp),
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun CommentsBlock(
    comments: List<ExportCommentRow>,
    moreCount: Int,
    labelColor: Color,
    bodyColor: Color,
    metaColor: Color
) {
    Column {
        ShareSectionLabel(stringResource(R.string.export_comments_heading, comments.size + moreCount), labelColor)
        Spacer(modifier = Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            comments.forEach { comment ->
                Row {
                    PersonAvatar(
                        initials = comment.authorInitials ?: "?",
                        accentKey = comment.authorAccentKey ?: "accentA",
                        size = 56.dp,
                        photoUri = comment.authorPhotoUri
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = comment.body,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 29.sp, lineHeight = 40.sp),
                            color = bodyColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = listOfNotNull(comment.authorLabel, comment.timestampLabel).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 21.sp),
                            color = metaColor
                        )
                    }
                }
            }
        }
        if (moreCount > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.export_more_count, moreCount),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 21.sp, fontWeight = FontWeight.Medium),
                color = metaColor
            )
        }
    }
}

@Composable
private fun ShareFooter(
    sharedByName: String,
    sharedByInitials: String,
    sharedByAccentKey: String,
    sharedByPhotoUri: String?,
    sharedOnLabel: String,
    bandColor: Color,
    nameColor: Color,
    metaColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bandColor)
            .padding(horizontal = 64.dp, vertical = 34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(
            initials = sharedByInitials,
            accentKey = sharedByAccentKey,
            size = 56.dp,
            photoUri = sharedByPhotoUri
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = sharedByName,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                color = nameColor
            )
            Text(
                text = sharedOnLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp, fontFamily = FontFamily.Monospace),
                color = metaColor
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.export_share_tagline),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp, fontFamily = FontFamily.Monospace),
                color = metaColor
            )
            Text(
                text = ShareRepoUrl,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp, fontFamily = FontFamily.Monospace),
                color = metaColor
            )
        }
    }
}

private const val ShareRepoUrl = "github.com/rjwarrier/yata"

/**
 * Renders [TaskShareCard] off-screen and rasterizes it at [targetWidthPx] — pins [LocalDensity]
 * to `targetWidthPx / 1080f` (fontScale locked to 1) so the card's spec-literal dp/sp values
 * produce a pixel-exact bitmap of that width regardless of the real device's display density or
 * the user's system font scale, which would otherwise reflow a layout tuned to exact pixel specs.
 */
suspend fun captureTaskShareBitmap(
    context: android.content.Context,
    targetWidthPx: Int,
    content: @Composable () -> Unit
): android.graphics.Bitmap {
    val scale = targetWidthPx / ShareCanvasWidthDp.toFloat()
    return captureComposableToBitmap(context, targetWidthPx) {
        CompositionLocalProvider(LocalDensity provides Density(density = scale, fontScale = 1f)) {
            content()
        }
    }
}
