package io.github.giova.metrofortaleza

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.giova.metrofortaleza.data.AppSettings
import io.github.giova.metrofortaleza.data.SettingsStore
import io.github.giova.metrofortaleza.ui.AppRoot
import io.github.giova.metrofortaleza.ui.theme.MetroFortalezaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by SettingsStore.flow(this).collectAsState()
            val current = settings ?: AppSettings()
            val dark = when (current.themeMode) {
                AppSettings.THEME_LIGHT -> false
                AppSettings.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            // Os ícones da barra de status acompanham o tema do app, não o do
            // celular: com "Claro" escolhido no celular escuro, ficariam
            // brancos sobre fundo branco.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            MetroFortalezaTheme(darkTheme = dark, wallpaperColors = current.wallpaperColors) {
                AppRoot()
            }
        }
    }
}
