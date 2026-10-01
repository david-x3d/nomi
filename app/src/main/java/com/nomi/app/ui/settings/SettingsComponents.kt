package com.nomi.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiCardShadowElevation
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardContainerColor
import com.nomi.app.ui.components.nomiCardTonalElevation
import com.nomi.app.ui.feedback.nomiPress
import com.nomi.app.ui.feedback.rememberNomiPressFeedback
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiPageContainerColor

/*
 * The rows and the page frame every settings screen is built from. Settings and the pages under it
 * share them, so a sub-page reads as the same place one level down rather than as another app.
 */

internal val SettingsRowShape = RoundedCornerShape(22.dp)

/** The canvas Settings and every page under it sit on. */
@Composable
internal fun settingsPageColor(): Color = nomiPageContainerColor(
    accent = MaterialTheme.colorScheme.tertiaryContainer,
    strength = 0.09f,
)

/** A page reached from Settings: the same canvas, a title and the way back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubpageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val pageColor = settingsPageColor()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = pageColor,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = nomiString("Back"),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageColor,
                    scrolledContainerColor = pageColor,
                ),
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 6.dp)
            .semantics { heading() },
    )
}

@Composable
internal fun SettingsLink(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconColor: Color = Color.Unspecified,
    supportingColor: Color = Color.Unspecified,
) {
    SettingSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(supporting, color = supportingColor) },
            leadingContent = { SettingsIconTile(iconColor.orPrimary(), icon) },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
internal fun ToggleSetting(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    iconColor: Color = Color.Unspecified,
) {
    SettingSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = { onClick?.invoke() ?: onCheckedChange(!checked) },
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(supporting) },
            leadingContent = { SettingsIconTile(iconColor.orPrimary(), icon) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/** A row that states something and leads nowhere. */
@Composable
internal fun SettingsInfo(
    icon: @Composable () -> Unit,
    title: String,
    supporting: String,
    iconColor: Color,
) {
    SettingsCard {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(supporting) },
            leadingContent = { SettingsIconTile(iconColor, icon) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/** The surface a settings row is cut from, for content that is more than one row. */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = SettingsRowShape,
        color = nomiCardContainerColor(),
        tonalElevation = nomiCardTonalElevation(),
        shadowElevation = NomiCardShadowElevation,
        border = nomiCardBorder(),
        content = content,
    )
}

@Composable
private fun SettingSurface(
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val press = rememberNomiPressFeedback(pressedScale = 0.985f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = press.interactionSource,
        modifier = modifier.nomiPress(press),
        shape = SettingsRowShape,
        color = nomiCardContainerColor(),
        tonalElevation = nomiCardTonalElevation(),
        shadowElevation = NomiCardShadowElevation,
        border = nomiCardBorder(),
        content = content,
    )
}

@Composable
internal fun SettingsIconTile(
    color: Color,
    icon: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Box(
            modifier = Modifier.padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Composable
private fun Color.orPrimary(): Color =
    if (this == Color.Unspecified) MaterialTheme.colorScheme.primary else this
