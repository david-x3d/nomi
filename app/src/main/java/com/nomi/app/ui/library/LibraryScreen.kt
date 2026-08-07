package com.nomi.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.localization.nomiString
import kotlin.math.roundToInt

enum class LibraryItemKind { RECENT, FAVORITE, SAVED_MEAL }

data class LibraryItem(
    val id: Long,
    val kind: LibraryItemKind,
    val title: String,
    val subtitle: String,
    val calories: Double,
)

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
    modifier: Modifier = Modifier,
) {
    var selected by remember(initialKind) { mutableStateOf(initialKind) }
    val items = when (selected) {
        LibraryItemKind.RECENT -> state.recent
        LibraryItemKind.FAVORITE -> state.favorites
        LibraryItemKind.SAVED_MEAL -> state.savedMeals
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(nomiString("Food library", "Lebensmittelbibliothek")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = nomiString("Back", "Zurück"),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LibraryItemKind.entries.forEach { kind ->
                        AssistChip(
                            onClick = { selected = kind },
                            label = { Text(kind.localizedLabel()) },
                            leadingIcon = { Icon(kind.icon, contentDescription = null) },
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(selected.localizedEmptyTitle(), style = MaterialTheme.typography.titleLarge)
                        Text(selected.localizedEmptyBody(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(items, key = { "${it.kind}-${it.id}" }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(item.subtitle) },
                        leadingContent = { Icon(item.kind.icon, contentDescription = null) },
                        trailingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${item.calories.roundToInt()} kcal")
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onAdd(item) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LibraryItemKind.localizedLabel(): String = when (this) {
        LibraryItemKind.RECENT -> nomiString("Recent", "Zuletzt")
        LibraryItemKind.FAVORITE -> nomiString("Favorites", "Favoriten")
        LibraryItemKind.SAVED_MEAL -> nomiString("Saved meals", "Gespeicherte Mahlzeiten")
    }

private val LibraryItemKind.icon: ImageVector
    get() = when (this) {
        LibraryItemKind.RECENT -> Icons.Default.History
        LibraryItemKind.FAVORITE -> Icons.Default.Favorite
        LibraryItemKind.SAVED_MEAL -> Icons.Default.RestaurantMenu
    }

@Composable
private fun LibraryItemKind.localizedEmptyTitle(): String = when (this) {
        LibraryItemKind.RECENT -> nomiString("No recent foods yet", "Noch keine kürzlich verwendeten Lebensmittel")
        LibraryItemKind.FAVORITE -> nomiString("No favorites yet", "Noch keine Favoriten")
        LibraryItemKind.SAVED_MEAL -> nomiString("No saved meals yet", "Noch keine gespeicherten Mahlzeiten")
    }

@Composable
private fun LibraryItemKind.localizedEmptyBody(): String = when (this) {
        LibraryItemKind.RECENT -> nomiString("Foods you log will be easy to reuse here.", "Hier kannst du eingetragene Lebensmittel schnell wiederverwenden.")
        LibraryItemKind.FAVORITE -> nomiString("Mark a food as a favorite from its details.", "Markiere ein Lebensmittel in den Details als Favorit.")
        LibraryItemKind.SAVED_MEAL -> nomiString("Save a group of logged foods to add it again in one tap.", "Speichere mehrere eingetragene Lebensmittel, um sie mit einem Tippen erneut hinzuzufügen.")
    }
