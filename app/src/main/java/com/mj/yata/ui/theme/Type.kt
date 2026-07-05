package com.mj.yata.ui.theme

import com.mj.yata.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AppTypographyFamily(
    val displayFamily: FontFamily,
    val bodyFamily: FontFamily,
)

// Registering more weight steps against the same variable font file lets Android resolve the
// exact instance on its `wght` axis for each — not just clamp everything to the nearest of 4 stops.
val InterTightFamily = FontFamily(
    Font(R.font.inter_tight_variable, FontWeight.Normal),
    Font(R.font.inter_tight_variable, FontWeight.Medium),
    Font(R.font.inter_tight_variable, FontWeight.SemiBold),
    Font(R.font.inter_tight_variable, FontWeight.Bold),
    Font(R.font.inter_tight_variable, FontWeight.ExtraBold),
    Font(R.font.inter_tight_variable, FontWeight.Black),
)

val InterBodyFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold),
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_variable, FontWeight.Normal),
    Font(R.font.jetbrains_mono_variable, FontWeight.Medium),
    Font(R.font.jetbrains_mono_variable, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_variable, FontWeight.Bold),
    Font(R.font.jetbrains_mono_variable, FontWeight.ExtraBold),
)

val InterTypographyFamily = AppTypographyFamily(
    displayFamily = InterTightFamily,
    bodyFamily = InterBodyFamily,
)

val MonoTypographyFamily = AppTypographyFamily(
    displayFamily = JetBrainsMonoFamily,
    bodyFamily = JetBrainsMonoFamily,
)

fun typographyFamilyFor(font: com.mj.yata.domain.model.AppFont): AppTypographyFamily = when (font) {
    com.mj.yata.domain.model.AppFont.INTER -> InterTypographyFamily
    com.mj.yata.domain.model.AppFont.JETBRAINS_MONO -> MonoTypographyFamily
}

fun createTypography(fonts: AppTypographyFamily = InterTypographyFamily) = Typography(
    displayLarge = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp
    ),
    displaySmall = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.05).sp
    ),
    titleMedium = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fonts.displayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fonts.bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fonts.bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fonts.bodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fonts.bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fonts.bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fonts.bodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    )
)

val Typography = createTypography(InterTypographyFamily)
