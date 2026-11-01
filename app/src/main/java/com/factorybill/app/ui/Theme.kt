package com.factorybill.app.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val AccentPalette = listOf(0xFF0E7A5A, 0xFF4F46E5, 0xFFE11D48, 0xFFF59E0B, 0xFF0891B2, 0xFF9333EA).map { Color(it) }

@Composable
fun FactoryTheme(mode: ThemeMode, accent: Color, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val scheme = if (dark) darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF174B39),
        onPrimaryContainer = Color(0xFFD4F5E3),
        secondary = Color(0xFFB2C7B9),
        onSecondary = Color(0xFF1D3328),
        secondaryContainer = Color(0xFF30483A),
        onSecondaryContainer = Color(0xFFDCEEE1),
        tertiary = Color(0xFFE0C28F),
        background = Color(0xFF101512),
        surface = Color(0xFF171E19),
        surfaceVariant = Color(0xFF29332D),
        onSurface = Color(0xFFE1E9E3),
        onSurfaceVariant = Color(0xFFB5C2B9),
        outline = Color(0xFF829187),
        error = Color(0xFFFFB4AB),
    ) else lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3F2E2),
        onPrimaryContainer = Color(0xFF002114),
        secondary = Color(0xFF52685A),
        secondaryContainer = Color(0xFFDCE9DE),
        onSecondaryContainer = Color(0xFF102117),
        tertiary = Color(0xFF745B30),
        background = Color(0xFFF3F7F4),
        surface = Color.White,
        surfaceVariant = Color(0xFFE8EFEB),
        onSurface = Color(0xFF18201B),
        onSurfaceVariant = Color(0xFF4D5C52),
        outline = Color(0xFF7C8B81),
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.1).sp),
            headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
            headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
            titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

private val Context.ds by preferencesDataStore("settings")

class SettingsStore(private val ctx: Context) {
    private val THEME = stringPreferencesKey("theme")
    private val ACCENT = intPreferencesKey("accent")
    val theme: Flow<ThemeMode> = ctx.ds.data.map { p -> runCatching { ThemeMode.valueOf(p[THEME] ?: "LIGHT") }.getOrDefault(ThemeMode.LIGHT) }
    val accent: Flow<Int> = ctx.ds.data.map { (it[ACCENT] ?: 0).coerceIn(0, AccentPalette.lastIndex) }

    suspend fun setTheme(m: ThemeMode) { ctx.ds.edit { it[THEME] = m.name } }
    suspend fun setAccent(i: Int) { ctx.ds.edit { it[ACCENT] = i } }
}
