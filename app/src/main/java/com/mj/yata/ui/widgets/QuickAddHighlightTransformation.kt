package com.mj.yata.ui.widgets

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.mj.yata.util.QuickAddHighlightSpan
import com.mj.yata.util.QuickAddHighlightType

@Composable
fun rememberQuickAddHighlightTransformation(
    spans: List<QuickAddHighlightSpan>,
    enabled: Boolean
): VisualTransformation {
    val fallbackColor = MaterialTheme.colorScheme.primary
    return remember(spans, enabled, fallbackColor) {
        VisualTransformation { text ->
            if (!enabled || spans.isEmpty()) {
                TransformedText(text, OffsetMapping.Identity)
            } else {
                val annotated = buildAnnotatedString {
                    append(text.text)
                    spans.forEach { span ->
                        val range = span.range
                        val chipColor = quickAddHighlightColor(span.type, fallbackColor)
                        val start = range.first.coerceIn(0, text.text.length)
                        val end = (range.last + 1).coerceIn(0, text.text.length)
                        if (start < end) {
                            addStyle(
                                SpanStyle(
                                    color = chipColor,
                                    fontWeight = FontWeight.Bold,
                                    background = chipColor.copy(alpha = 0.16f)
                                ),
                                start,
                                end
                            )
                        }
                    }
                }
                TransformedText(annotated, OffsetMapping.Identity)
            }
        }
    }
}

private fun quickAddHighlightColor(type: QuickAddHighlightType, fallbackColor: Color): Color = when (type) {
    QuickAddHighlightType.DueDate -> Color(0xFF2563EB)
    QuickAddHighlightType.StartDate -> Color(0xFF0891B2)
    QuickAddHighlightType.Time -> Color(0xFF7C3AED)
    QuickAddHighlightType.Recurrence -> Color(0xFF0F766E)
    QuickAddHighlightType.Reminder -> Color(0xFFD97706)
    QuickAddHighlightType.Priority -> Color(0xFFDC2626)
    QuickAddHighlightType.Flag -> Color(0xFFE11D48)
    QuickAddHighlightType.Project -> Color(0xFF9333EA)
    QuickAddHighlightType.List -> Color(0xFF4F46E5)
    QuickAddHighlightType.Tag -> Color(0xFF16A34A)
    QuickAddHighlightType.Assignee -> Color(0xFFDB2777)
    QuickAddHighlightType.Other -> fallbackColor
}
