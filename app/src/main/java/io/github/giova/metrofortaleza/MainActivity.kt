package io.github.giova.metrofortaleza

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.giova.metrofortaleza.ui.AppRoot
import io.github.giova.metrofortaleza.ui.theme.MetroFortalezaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MetroFortalezaTheme {
                AppRoot()
            }
        }
    }
}
