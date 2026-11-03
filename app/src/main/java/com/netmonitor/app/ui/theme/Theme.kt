package com.netmonitor.app.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netmonitor.app.data.AppSettings
import com.netmonitor.app.data.ThemeMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.Modifier

val Blue = Color(0xFF185FA5)
val DownBlue = Color(0xFF378ADD)
val UpGreen = Color(0xFF639922)
val MobileOrange = Color(0xFFD85A30)

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECF0),
    onSurface = Color(0xFF14181F),
    onSurfaceVariant = Color(0xFF5F6673),
    outlineVariant = Color(0xFFE1E4E9),
)

private val Dark = darkColorScheme(
    primary = DownBlue,
    onPrimary = Color.White,
    background = Color(0xFF000000),
    surface = Color(0xFF111113),
    surfaceVariant = Color(0xFF1C1C1F),
    onSurface = Color(0xFFF2F3F5),
    onSurfaceVariant = Color(0xFF9EA2AA),
    outlineVariant = Color(0xFF26272B),
)

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

@Composable
fun NetMonitorTheme(content: @Composable () -> Unit) {
    val mode by AppSettings.state.collectAsStateWithLifecycle()
    val dark = when (mode.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) Dark else Light
    MaterialTheme(
        colorScheme = scheme,
    ) {
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalContentColor provides scheme.onSurface,
            content = content,
        )
    }
}
