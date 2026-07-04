package com.mj.yata.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

object UiSpacing {
    val xSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xLarge = 20.dp
    val xxLarge = 24.dp
}

object UiSize {
    val iconSmall = 18.dp
    val iconMedium = 20.dp
    val touchTarget = 48.dp
    val leadingContainer = 36.dp
}

object UiPadding {
    val screenHorizontal = 20.dp
    val screenSection = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
    val listRow = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    val chip = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
}

object UiShape {
    val pill = RoundedCornerShape(999.dp)
}

object UiElevation {
    val card = 2.dp
}

fun Modifier.pillClip(): Modifier = clip(UiShape.pill)

@Composable
fun appDividerColor() = MaterialTheme.colorScheme.outlineVariant
