package com.nomi.app.ui.capture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.MenuDish
import com.nomi.app.ui.components.NomiInlineError
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.components.nomiCardElevation
import com.nomi.app.ui.components.nomiCardShape
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import java.util.Locale

data class MenuScanUiState(
    val restaurantName: String? = null,
    val items: List<MenuDish> = emptyList(),
    val query: String = "",
    val pageCount: Int = 0,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val notes: List<String> = emptyList(),
    val selectedDishKeys: Set<String> = emptySet(),
)

internal fun menuDishKey(dish: MenuDish): String =
    listOf(dish.category, dish.number, dish.name, dish.price)
        .joinToString("|") { it.orEmpty().trim().lowercase(Locale.ROOT) }

internal fun filteredMenuDishes(items: List<MenuDish>, query: String): List<MenuDish> {
    val terms = query.trim().lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
    if (terms.isEmpty()) return items
    return items.filter { dish ->
        val searchable = listOfNotNull(
            dish.number,
            dish.name,
            dish.description,
            dish.category,
            dish.price,
        ).joinToString(" ").lowercase(Locale.ROOT)
        terms.all(searchable::contains)
    }
}

internal fun mergeMenuDishes(existing: List<MenuDish>, added: List<MenuDish>): List<MenuDish> {
    val byIdentity = linkedMapOf<String, MenuDish>()
    (existing + added).forEach { dish ->
        val key = menuDishKey(dish)
        val current = byIdentity[key]
        byIdentity[key] = if ((dish.description?.length ?: 0) > (current?.description?.length ?: 0)) {
            dish
        } else {
            current ?: dish
        }
    }
    return byIdentity.values.toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScanScreen(
    state: MenuScanUiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onAddPage: () -> Unit,
    onToggleDish: (MenuDish) -> Unit,
    onAddSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = filteredMenuDishes(state.items, state.query)
    val defaultCategory = nomiString("Menu")
    val grouped = filtered.groupBy { dish ->
        dish.category?.trim()?.takeIf(String::isNotBlank)
            ?: defaultCategory
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(nomiString("Menu")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = nomiString("Back"))
                    }
                },
                actions = {
                    IconButton(onClick = onAddPage, enabled = !state.isProcessing) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = nomiString("Add another menu page"))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.isProcessing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (state.items.isEmpty() && state.isProcessing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text(
                            nomiString("Reading the complete menu..."),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            nomiString("Gemini is extracting names, descriptions, numbers and prices."),
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.restaurantName?.takeIf(String::isNotBlank)?.let { restaurant ->
                            Text(restaurant, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            nomiFormat(
                                "{0} of {1} dishes \u00b7 {2} page(s)",
                                filtered.size,
                                state.items.size,
                                state.pageCount,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        NomiTextField(
                            value = state.query,
                            onValueChange = onQueryChanged,
                            leadingIcon = Icons.Default.Search,
                            label = nomiString("Search dishes, ingredients or numbers"),
                        )
                        state.errorMessage?.let { NomiInlineError(it) }
                    }
                }

                if (filtered.isEmpty() && !state.isProcessing) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                if (state.query.isBlank()) {
                                    nomiString("No dishes were readable.")
                                } else {
                                    nomiString("No matching dishes")
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Button(onClick = onAddPage) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(nomiString("Add a clearer page"))
                            }
                        }
                    }
                }

                grouped.forEach { (category, dishes) ->
                    item(key = "category-$category") {
                        Text(
                            category,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
                                .semantics { heading() },
                        )
                    }
                    itemsIndexed(
                        items = dishes,
                        key = { index, dish -> "$category-${dish.number}-${dish.name}-$index" },
                    ) { _, dish ->
                        MenuDishCard(
                            dish = dish,
                            selected = menuDishKey(dish) in state.selectedDishKeys,
                            onClick = { onToggleDish(dish) },
                        )
                    }
                }

                if (state.items.isNotEmpty()) {
                    item {
                        Button(
                            onClick = onAddPage,
                            enabled = !state.isProcessing,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(nomiString("Photograph another page"))
                        }
                    }
                }
            }
            if (state.selectedDishKeys.isNotEmpty()) {
                Button(
                    onClick = onAddSelected,
                    enabled = !state.isProcessing,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    val count = state.selectedDishKeys.size
                    Text(
                        // Singular and plural are separate catalogue entries so each language
                        // can pick its own wording rather than bolting an "s" onto a noun.
                        nomiFormat(
                            if (count == 1) "Add {0} selected dish" else "Add {0} selected dishes",
                            count,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuDishCard(dish: MenuDish, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = nomiCardShape(24.dp),
        elevation = nomiCardElevation(),
        border = nomiCardBorder(),
    ) {
        ListItem(
            leadingContent = dish.number?.takeIf(String::isNotBlank)?.let { number ->
                {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            number,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            },
            headlineContent = { Text(dish.name, fontWeight = FontWeight.SemiBold) },
            supportingContent = dish.description?.takeIf(String::isNotBlank)?.let { description ->
                {
                    Text(
                        description,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    dish.price?.takeIf(String::isNotBlank)?.let { price ->
                        Text(price, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Checkbox(checked = selected, onCheckedChange = null)
                }
            },
        )
    }
}
