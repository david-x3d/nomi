package com.nomi.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Shape
import com.nomi.app.ui.theme.LocalPitchBlackSurfaces
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
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
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardContainerColor
import com.nomi.app.ui.feedback.nomiPress
import com.nomi.app.ui.feedback.rememberNomiPressFeedback
import com.nomi.app.ui.localization.nomiString

/*
 * The rows and the page frame every settings screen is built from. Settings and the pages under it
 * share them, so a sub-page reads as the same place one level down rather than as another app.
 */

internal val SettingsRowShape = RoundedCornerShape(26.dp)

private val GroupOuterRadius = 26.dp
private val GroupInnerRadius = 6.dp

/** Shape the next settings tile is cut to; rows inside a [settingsGroup] get joined corners. */
internal val LocalSettingsTileShape = compositionLocalOf<Shape> { SettingsRowShape }

/** The canvas Settings and every page under it sit on. */
@Composable
internal fun settingsPageColor(): Color =
    if (LocalPitchBlackSurfaces.current) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

@Composable
private fun settingsTileColor(): Color =
    if (LocalPitchBlackSurfaces.current) nomiCardContainerColor() else MaterialTheme.colorScheme.surfaceBright

/**
 * A titled group of rows drawn as separate tiles: large outer corners, small inner ones, a thin
 * gap between them. Each row lambda is a composable that calls SettingsLink, ToggleSetting etc.
 */
internal fun LazyListScope.settingsGroup(
    title: (@Composable () -> String)? = null,
    key: String,
    rows: List<@Composable () -> Unit>,
) {
    if (title != null) {
        item(key = "h-$key") { SectionTitle(title()) }
    } else {
        item(key = "h-$key") { Spacer(Modifier.height(12.dp)) }
    }
    rows.forEachIndexed { index, row ->
        item(key = "$key-$index") {
            val first = index == 0
            val last = index == rows.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (first) GroupOuterRadius else GroupInnerRadius,
                topEnd = if (first) GroupOuterRadius else GroupInnerRadius,
                bottomStart = if (last) GroupOuterRadius else GroupInnerRadius,
                bottomEnd = if (last) GroupOuterRadius else GroupInnerRadius,
            )
            CompositionLocalProvider(LocalSettingsTileShape provides shape) { row() }
        }
    }
}

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
                    FilledTonalIconButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
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
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 36.dp, end = 24.dp, top = 22.dp, bottom = 8.dp)
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
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 3.dp),
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
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 3.dp),
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
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 3.dp),
        shape = LocalSettingsTileShape.current,
        color = settingsTileColor(),
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
        shape = LocalSettingsTileShape.current,
        color = settingsTileColor(),
        border = nomiCardBorder(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val BadgeShapes = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Flower,
    MaterialShapes.SoftBurst,
)

/** A tonal badge in a Material shape; known accents use their container pair like Wisp does. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsIconTile(
    color: Color,
    icon: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (color) {
        scheme.primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        scheme.secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        scheme.tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        scheme.error -> scheme.errorContainer to scheme.onErrorContainer
        else -> color.copy(alpha = 0.16f) to color
    }
    // The accent colour picks the shape, so the same kind of row always gets the same badge.
    val shape = BadgeShapes[(color.value.toLong().hashCode() and 0x7fffffff) % BadgeShapes.size]
    Surface(shape = shape.toShape(), color = bg, contentColor = fg) {
        Box(modifier = Modifier.padding(11.dp), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun Color.orPrimary(): Color =
    if (this == Color.Unspecified) MaterialTheme.colorScheme.primary else this
