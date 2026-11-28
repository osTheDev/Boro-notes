package com.boro.notes.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App colour themes. [PHONE] follows the phone's wallpaper colours; the rest are boro/workwear dyes. */
enum class ThemePreset(val label: String, val primary: Color?, val secondary: Color?, val tertiary: Color?) {
    PHONE("Phone", null, null, null),
    INDIGO("Indigo", Color(0xFF2E4A7D), Color(0xFF8C6A4F), Color(0xFFB5562B)),
    DENIM("Denim", Color(0xFF3A5A86), Color(0xFF5D6B7A), Color(0xFFC9A227)),
    RUST("Rust", Color(0xFFA6492A), Color(0xFF6E5B4C), Color(0xFF2E4A7D)),
    SAGE("Sage", Color(0xFF4E7550), Color(0xFF7A6A52), Color(0xFF9C3D35)),
    OCHRE("Ochre", Color(0xFF9A6B12), Color(0xFF5E6B4F), Color(0xFF2E4A7D)),
    PLUM("Plum", Color(0xFF6F3A66), Color(0xFF6B5E70), Color(0xFFC08A1E)),
    CHARCOAL("Charcoal", Color(0xFF45494F), Color(0xFF7A6A52), Color(0xFFB5562B)),
}

enum class DarkMode(val label: String) { AUTO("Auto"), LIGHT("Light"), DARK("Dark") }

/** Sashiko patterns for the home screen background. */
enum class HomePattern(val label: String) { NONE("None"), JUJI("Crosses"), SEIGAIHA("Waves"), STITCH("Running stitch"), DOTS("Dots") }

/** Everything the person has customised about the look of the app. */
data class LookSettings(
    val theme: ThemePreset = ThemePreset.INDIGO,
    val mode: DarkMode = DarkMode.AUTO,
    val pattern: HomePattern = HomePattern.JUJI,
    /** Home tile colour per section name, as an index into the tile palette. */
    val tileColors: Map<String, Int> = emptyMap(),
)

/** Saves [LookSettings] on the phone. */
class LookPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("look", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<LookSettings> = _settings.asStateFlow()

    fun update(s: LookSettings) {
        _settings.value = s
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("mode", s.mode.name)
            .putString("pattern", s.pattern.name)
            .putString("tiles", s.tileColors.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    private fun load() = LookSettings(
        theme = enumOr(prefs.getString("theme", null), ThemePreset.INDIGO),
        mode = enumOr(prefs.getString("mode", null), DarkMode.AUTO),
        pattern = enumOr(prefs.getString("pattern", null), HomePattern.JUJI),
        tileColors = prefs.getString("tiles", "").orEmpty().split(',').mapNotNull { part ->
            val (k, v) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            v.toIntOrNull()?.let { k to it }
        }.toMap(),
    )

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}

private val PAPER = Color(0xFFFBF7EF)
private val NIGHT = Color(0xFF131418)

private fun schemeFor(p: ThemePreset, dark: Boolean): ColorScheme {
    val primary = p.primary!!
    val secondary = p.secondary!!
    val tertiary = p.tertiary!!
    return if (!dark) {
        val bg = lerp(PAPER, primary, 0.03f)
        lightColorScheme(
            primary = primary, onPrimary = Color.White,
            primaryContainer = lerp(primary, Color.White, 0.80f), onPrimaryContainer = lerp(primary, Color.Black, 0.55f),
            secondary = secondary, onSecondary = Color.White,
            secondaryContainer = lerp(secondary, Color.White, 0.80f), onSecondaryContainer = lerp(secondary, Color.Black, 0.55f),
            tertiary = tertiary, onTertiary = Color.White,
            tertiaryContainer = lerp(tertiary, Color.White, 0.80f), onTertiaryContainer = lerp(tertiary, Color.Black, 0.55f),
            background = bg, surface = bg,
            surfaceVariant = lerp(bg, primary, 0.08f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(bg, primary, 0.03f),
            surfaceContainer = lerp(bg, primary, 0.05f),
            surfaceContainerHigh = lerp(bg, primary, 0.08f),
            surfaceContainerHighest = lerp(bg, primary, 0.11f),
            outline = lerp(Color(0xFF7A756D), primary, 0.15f),
            outlineVariant = lerp(Color(0xFFD3CEC4), primary, 0.10f),
        )
    } else {
        val bg = lerp(NIGHT, primary, 0.08f)
        darkColorScheme(
            primary = lerp(primary, Color.White, 0.45f), onPrimary = lerp(primary, Color.Black, 0.6f),
            primaryContainer = lerp(primary, Color.Black, 0.35f), onPrimaryContainer = lerp(primary, Color.White, 0.82f),
            secondary = lerp(secondary, Color.White, 0.45f), onSecondary = lerp(secondary, Color.Black, 0.6f),
            secondaryContainer = lerp(secondary, Color.Black, 0.40f), onSecondaryContainer = lerp(secondary, Color.White, 0.82f),
            tertiary = lerp(tertiary, Color.White, 0.45f), onTertiary = lerp(tertiary, Color.Black, 0.6f),
            tertiaryContainer = lerp(tertiary, Color.Black, 0.40f), onTertiaryContainer = lerp(tertiary, Color.White, 0.82f),
            background = bg, surface = bg,
            surfaceVariant = lerp(bg, primary, 0.18f),
            surfaceContainerLowest = lerp(bg, Color.Black, 0.3f),
            surfaceContainerLow = lerp(bg, primary, 0.06f),
            surfaceContainer = lerp(bg, primary, 0.10f),
            surfaceContainerHigh = lerp(bg, primary, 0.14f),
            surfaceContainerHighest = lerp(bg, primary, 0.18f),
            outline = lerp(Color(0xFF8F8A82), primary, 0.15f),
            outlineVariant = lerp(Color(0xFF45433F), primary, 0.15f),
        )
    }
}

@Composable
fun BoroTheme(look: LookSettings = LookSettings(), content: @Composable () -> Unit) {
    val dark = when (look.mode) {
        DarkMode.AUTO -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val colors = when {
        look.theme != ThemePreset.PHONE -> schemeFor(look.theme, dark)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
