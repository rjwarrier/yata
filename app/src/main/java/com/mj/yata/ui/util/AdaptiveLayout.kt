package com.mj.yata.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AdaptiveLayoutInfo(
    val isWide: Boolean,
    val contentMaxWidth: Dp
)

@Composable
fun rememberAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return AdaptiveLayoutInfo(
        isWide = screenWidthDp >= 600,
        contentMaxWidth = when {
            screenWidthDp >= 1200 -> 1040.dp
            screenWidthDp >= 840 -> 920.dp
            screenWidthDp >= 600 -> 760.dp
            else -> Dp.Unspecified
        }
    )
}

@Composable
fun AdaptiveContentBox(
    modifier: Modifier = Modifier,
    contentMaxWidth: Dp = rememberAdaptiveLayoutInfo().contentMaxWidth,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .then(
                    if (contentMaxWidth == Dp.Unspecified) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = contentMaxWidth)
                    }
                ),
            content = content
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun rememberAdaptiveSheetMaxWidth(maxWidth: Dp = 640.dp): Dp {
    return if (rememberAdaptiveLayoutInfo().isWide) maxWidth else BottomSheetDefaults.SheetMaxWidth
}
