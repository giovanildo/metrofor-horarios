package io.github.giova.metrofortaleza.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.toColorInt

private val MetroRed = Color(0xFFED1C24)

private val LightColors = lightColorScheme(
    primary = MetroRed,
    onPrimary = Color.White,
    secondary = Color(0xFF8C3A3D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A85),
    onPrimary = Color(0xFF5F1013),
    secondary = Color(0xFFE7B4B2),
)

@Composable
fun MetroFortalezaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        // Cor dinâmica a partir do papel de parede, quando o sistema oferece.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Converte a cor da linha vinda do GTFS (`"ed1c24"`) em uma [Color]. */
fun routeColor(hex: String): Color = runCatching {
    Color("#$hex".toColorInt())
}.getOrDefault(MetroRed)
