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
        background = Color(0xFF111714),
        surface = Color(0xFF1A211D),
        surfaceVariant = Color(0xFF26312B),
    ) else lightColorScheme(
        primary = accent,
        background = Color(0xFFF3F7F4),
        surface = Color.White,
        surfaceVariant = Color(0xFFE8EFEB),
    )
    MaterialTheme(
        colorScheme = scheme,
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
    val theme: Flow<ThemeMode> = ctx.ds.data.map { p -> runCatching { ThemeMode.valueOf(p[THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM) }
    val accent: Flow<Int> = ctx.ds.data.map { (it[ACCENT] ?: 0).coerceIn(0, AccentPalette.lastIndex) }

    suspend fun setTheme(m: ThemeMode) { ctx.ds.edit { it[THEME] = m.name } }
    suspend fun setAccent(i: Int) { ctx.ds.edit { it[ACCENT] = i } }
}
