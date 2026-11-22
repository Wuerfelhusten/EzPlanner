package com.moddingforge.ezplanner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF0F1114)
private val Surface = Color(0xFF181B20)
private val SurfaceHigh = Color(0xFF22262D)
private val SurfaceHighest = Color(0xFF2B3038)
private val Primary = Color(0xFF7FA7FF)
private val OnPrimary = Color(0xFF0B1A3A)
private val PrimaryContainer = Color(0xFF1F335F)
private val OnSurface = Color(0xFFE6E8EC)
private val OnSurfaceVariant = Color(0xFFA4AAB4)

private val ColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF9BB4E6),
    secondaryContainer = Color(0xFF26344F),
    onSecondaryContainer = Color(0xFFDCE5FF),
    tertiary = Color(0xFF4CD08A),
    background = Background,
    onBackground = OnSurface,
    surface = Background,
    onSurface = OnSurface,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = SurfaceHighest,
    outline = Color(0xFF4A505A),
    outlineVariant = Color(0xFF30353D),
    error = Color(0xFFFF6B6B),
)

/** Semantic colors for money. */
@Immutable
data class MoneyColors(
    val income: Color = Color(0xFF4CD08A),
    val expense: Color = Color(0xFFFF6B6B),
    val transfer: Color = Color(0xFF7FA7FF),
    val adjustment: Color = Color(0xFFB18CFF),
    val warning: Color = Color(0xFFFFB454),
)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors() }

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
    )
}

@Composable
fun EzTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography) {
        androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides MoneyColors()) {
            content()
        }
    }
}

object Ez {
    val money: MoneyColors
        @Composable get() = LocalMoneyColors.current
}
