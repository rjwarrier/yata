package com.mj.yata.ui.widgets

import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon

/** Renders markdown (bold, italic, lists, links, ...) using Markwon inside a plain TextView.
 *
 * A plain TextView doesn't pick up Compose's ambient font automatically — without resolving
 * and applying it explicitly, this always rendered in the system default typeface regardless
 * of the Inter/JetBrains Mono choice in Settings. */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val markwon = remember(context) { Markwon.create(context) }
    val textColor = LocalContentColor.current.toArgb()
    val textStyle = LocalTextStyle.current
    val textSizeSp = textStyle.fontSize.value

    val fontFamilyResolver = LocalFontFamilyResolver.current
    val typeface = fontFamilyResolver.resolve(
        fontFamily = textStyle.fontFamily,
        fontWeight = textStyle.fontWeight ?: FontWeight.Normal
    ).value as? android.graphics.Typeface

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(textColor)
                textSize = textSizeSp
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.textSize = textSizeSp
            textView.typeface = typeface
            markwon.setMarkdown(textView, markdown)
        }
    )
}
