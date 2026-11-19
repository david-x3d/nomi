package com.nomi.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.ExpressiveBadgeStyle
import com.nomi.app.ui.components.NomiCard
import com.nomi.app.ui.components.NomiExpressiveBadge
import com.nomi.app.ui.components.NomiTonalBackButton
import com.nomi.app.ui.components.expressiveGroupedTileShape
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardContainerColor
import com.nomi.app.ui.feedback.nomiPress
import com.nomi.app.ui.feedback.rememberNomiHaptics
import com.nomi.app.ui.feedback.rememberNomiPressFeedback
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.LocalPitchBlackSurfaces
import com.nomi.app.ui.theme.nomiPageContainerColor
import kotlin.math.roundToInt

enum class LibraryItemKind { RECENT, FAVORITE, SAVED_MEAL }

data class LibraryItem(
    val id: Long,
    val kind: LibraryItemKind,
    val title: String,
    val subtitle: String,
    val calories: Double,
) {
    val key: String get() = "$kind-$id"
}

data class LibraryUiState(
    val recent: List<LibraryItem> = emptyList(),
    val favorites: List<LibraryItem> = emptyList(),
    val savedMeals: List<LibraryItem> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    initialKind: LibraryItemKind,
    onBack: () -> Unit,
    onAdd: (LibraryItem) -> Unit,
    pendingDeletions: Map<String, Boolean>,
    onDelete: (LibraryItem) -> Unit,
    onUndoDelete: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable(initialKind) { mutableStateOf(initialKind) }
    val items = when (selected) {
        LibraryItemKind.RECENT -> state.recent
        LibraryItemKind.FAVORITE -> state.favorites
        LibraryItemKind.SAVED_MEAL -> state.savedMeals
    }
    val pageContainerColor = if (LocalPitchBlackSurfaces.current) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = pageContainerColor,
        topBar = {
            TopAppBar(
                title = { Text(nomiString("Food library")) },
                navigationIcon = { NomiTonalBackButton(onClick = onBack) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageContainerColor,
                    scrolledContainerColor = pageContainerColor,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(pageContainerColor),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LibraryItemKind.entries) { kind ->
                        FilterChip(
                            selected = kind == selected,
                            onClick = { selected = kind },
                            label = { Text(kind.localizedLabel()) },
                            leadingIcon = { Icon(kind.icon, contentDescription = null) },
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    val emptyTileColor = if (LocalPitchBlackSurfaces.current) {
                        nomiCardContainerColor()
                    } else {
                        MaterialTheme.colorScheme.surfaceBright
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateItem(),
                        shape = expressiveGroupedTileShape(0, 1),
                        color = emptyTileColor,
                        border = nomiCardBorder(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            NomiExpressiveBadge(
                                icon = selected.icon,
                                style = selected.badgeStyle(),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                padding = 12.dp,
                                iconSize = 22.dp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    selected.localizedEmptyTitle(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text(
                                    selected.localizedEmptyBody(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(items, key = { _, it -> "${it.kind}-${it.id}" }) { index, item ->
                    LibraryRow(
                        item = item,
                        shape = expressiveGroupedTileShape(index, items.size),
                        pending = pendingDeletions[item.key],
                        onAdd = { onAdd(item) },
                        onDelete = { onDelete(item) },
                        onUndo = { onUndoDelete(item) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryRow(
    item: LibraryItem,
    shape: Shape,
    pending: Boolean?,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberNomiHaptics()
    val deleteLabel = nomiString("Delete")
    val tileColor = if (LocalPitchBlackSurfaces.current) {
        nomiCardContainerColor()
    } else {
        MaterialTheme.colorScheme.surfaceBright
    }

    if (pending != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                Modifier
                    .heightIn(min = 72.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(item.title, Modifier.weight(1f), maxLines = 2)
                TextButton(onClick = { haptics.confirmed(); onUndo() }, enabled = pending) {
                    Text(nomiString("Undo"))
                }
            }
        }
        return
    }

    val (badgeBg, badgeFg) = when (item.kind) {
        LibraryItemKind.RECENT ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LibraryItemKind.FAVORITE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LibraryItemKind.SAVED_MEAL ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    val press = rememberNomiPressFeedback(pressedScale = 0.985f)
    val content: @Composable () -> Unit = {
        Surface(
            onClick = onAdd,
            interactionSource = press.interactionSource,
            shape = shape,
            color = tileColor,
            border = nomiCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .nomiPress(press)
                .semantics {
                    if (item.kind != LibraryItemKind.RECENT) {
                        customActions = listOf(
                            CustomAccessibilityAction(deleteLabel) {
                                haptics.removed()
                                onDelete()
                                true
                            },
                        )
                    }
                },
        ) {
            ListItem(
                headlineContent = {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                },
                supportingContent = { Text(item.subtitle) },
                leadingContent = {
                    NomiExpressiveBadge(
                        icon = item.kind.icon,
                        style = item.kind.badgeStyle(),
                        containerColor = badgeBg,
                        contentColor = badgeFg,
                        padding = 10.dp,
                        iconSize = 19.dp,
                    )
                },
                trailingContent = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "${item.calories.roundToInt()} kcal",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
    if (item.kind == LibraryItemKind.RECENT) {
        Box(modifier) { content() }
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.removed()
                onDelete()
                true
            } else {
                false
            }
        },
        positionalThreshold = { it * 0.32f },
    )
    val armed = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
    LaunchedEffect(armed) { if (armed) haptics.deleteArmed() }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(shape),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Text(deleteLabel, color = MaterialTheme.colorScheme.onErrorContainer)
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) { content() }
}

private fun LibraryItemKind.badgeStyle(): ExpressiveBadgeStyle = when (this) {
    LibraryItemKind.RECENT -> ExpressiveBadgeStyle.CLOVER_4
    LibraryItemKind.FAVORITE -> ExpressiveBadgeStyle.SOFT_BURST
    LibraryItemKind.SAVED_MEAL -> ExpressiveBadgeStyle.COOKIE_9
}

@Composable
private fun LibraryItemKind.localizedLabel(): String = when (this) {
    LibraryItemKind.RECENT -> nomiString("Recent")
    LibraryItemKind.FAVORITE -> nomiString("Favorites")
    LibraryItemKind.SAVED_MEAL -> nomiString("Saved meals")
}

private val LibraryItemKind.icon: ImageVector
    get() = when (this) {
        LibraryItemKind.RECENT -> Icons.Default.History
        LibraryItemKind.FAVORITE -> Icons.Default.Favorite
        LibraryItemKind.SAVED_MEAL -> Icons.Default.RestaurantMenu
    }

@Composable
private fun LibraryItemKind.localizedEmptyTitle(): String = when (this) {
    LibraryItemKind.RECENT -> nomiString("No recent foods yet")
    LibraryItemKind.FAVORITE -> nomiString("No favorites yet")
    LibraryItemKind.SAVED_MEAL -> nomiString("No saved meals yet")
}

@Composable
private fun LibraryItemKind.localizedEmptyBody(): String = when (this) {
    LibraryItemKind.RECENT -> nomiString("Foods you log will be easy to reuse here.")
    LibraryItemKind.FAVORITE -> nomiString("Mark a food as a favorite from its details.")
    LibraryItemKind.SAVED_MEAL -> nomiString("Save a group of logged foods to add it again in one tap.")
}
