package com.nomi.app.ui.logging

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ui.components.AnimatedWebsiteIconStack
import com.nomi.app.ui.components.WebsiteFavicon
import com.nomi.app.ui.format.quantityDisplay
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.today.MealCategory
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoodLoggingScreen(
    state: FoodLoggingUiState,
    onBack: () -> Unit,
    onTextChanged: (String) -> Unit,
    onMealCategoryChanged: (MealCategory) -> Unit,
    onAnalyze: () -> Unit,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onManualDraftChanged: (ManualFoodDraft) -> Unit,
    onEditItem: (Int) -> Unit,
    onChangePortion: (Int) -> Unit,
    onConfirm: () -> Unit,
    onPhotoDescriptionChanged: (String) -> Unit = {},
    onPhotoPlaceChanged: (String) -> Unit = {},
    onConfirmPhotoDescription: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(nomiString("Add food", "Essen hinzufügen")) },
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
    ) { innerPadding ->
        when (state) {
            is FoodLoggingUiState.Input -> InputContent(
                state = state,
                onTextChanged = onTextChanged,
                onMealCategoryChanged = onMealCategoryChanged,
                onAnalyze = onAnalyze,
                onManual = onManual,
                modifier = Modifier.padding(innerPadding),
            )

            is FoodLoggingUiState.Processing -> ProcessingContent(
                stage = state.stage,
                sourceUrls = state.sourceUrls,
                modifier = Modifier.padding(innerPadding),
            )

            is FoodLoggingUiState.PhotoReview -> PhotoReviewContent(
                state = state,
                onDescriptionChanged = onPhotoDescriptionChanged,
                onPlaceChanged = onPhotoPlaceChanged,
                onMealCategoryChanged = onMealCategoryChanged,
                onConfirm = onConfirmPhotoDescription,
                modifier = Modifier.padding(innerPadding),
            )

            is FoodLoggingUiState.Preview -> PreviewContent(
                analysis = state.analysis,
                mealCategory = state.mealCategory,
                onMealCategoryChanged = onMealCategoryChanged,
                onEditItem = onEditItem,
                onChangePortion = onChangePortion,
                onConfirm = onConfirm,
                modifier = Modifier.padding(innerPadding),
            )

            is FoodLoggingUiState.Error -> ErrorContent(
                state = state,
                onRetry = onRetry,
                onManual = onManual,
                modifier = Modifier.padding(innerPadding),
            )

            is FoodLoggingUiState.Manual -> ManualFoodContent(
                draft = state.draft,
                onDraftChanged = onManualDraftChanged,
                onConfirm = onConfirm,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun InputContent(
    state: FoodLoggingUiState.Input,
    onTextChanged: (String) -> Unit,
    onMealCategoryChanged: (MealCategory) -> Unit,
    onAnalyze: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            nomiString("What did you eat?", "Was hast du gegessen?"),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            nomiString(
                "Try “two slices of toast with butter and a banana” or “250 g Skyr and a banana”.",
                "Versuche zum Beispiel „zwei Scheiben Toast mit Butter und einer Banane“ oder „250 g Skyr und eine Banane“.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChanged,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            minLines = 3,
            maxLines = 7,
            placeholder = { Text(nomiString("Tell Nomi what you ate", "Sag Nomi, was du gegessen hast")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (state.text.isNotBlank()) onAnalyze() }),
            supportingText = { Text(nomiString("German and English work naturally.", "Deutsch und Englisch funktionieren ganz natürlich.")) },
        )
        MealCategorySelector(state.mealCategory, onMealCategoryChanged)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onAnalyze,
            enabled = state.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.padding(4.dp))
            Text(nomiString("Understand meal", "Mahlzeit verstehen"))
        }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
            Text(nomiString("Enter manually", "Manuell eingeben"))
        }
    }
}

/**
 * The checkpoint between seeing and looking up.
 *
 * The description is presented as the user's own sentence to edit, which is the cheapest place
 * in the whole flow to fix a misread: one word here, instead of a full nutrition search that
 * has to be thrown away and run again.
 */
@Composable
private fun PhotoReviewContent(
    state: FoodLoggingUiState.PhotoReview,
    onDescriptionChanged: (String) -> Unit,
    onPlaceChanged: (String) -> Unit,
    onMealCategoryChanged: (MealCategory) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                nomiString("Is this what you ate?", "Ist das, was du gegessen hast?"),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        item {
            Text(
                nomiString(
                    "Nomi read your photo as the words below. Fix anything it got wrong before it looks up the nutrition.",
                    "Nomi hat dein Foto als den folgenden Text gelesen. Korrigiere alles Falsche, bevor die Nährwerte recherchiert werden.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChanged,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
                label = { Text(nomiString("What's in the photo", "Was auf dem Foto ist")) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                supportingText = {
                    Text(
                        nomiString(
                            "Correct a food, an amount, or an ingredient — it reads like anything you'd type.",
                            "Korrigiere ein Lebensmittel, eine Menge oder eine Zutat – es liest sich wie alles, was du tippst.",
                        ),
                    )
                },
            )
        }
        item {
            OutlinedTextField(
                value = state.place,
                onValueChange = onPlaceChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(nomiString("Restaurant or shop (optional)", "Restaurant oder Laden (optional)")) },
                placeholder = { Text(nomiString("e.g. Five Guys", "z. B. Five Guys")) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.canContinue) onConfirm() }),
                supportingText = {
                    Text(
                        nomiString(
                            "Naming the place sends Nomi to its own published nutrition instead of a generic recipe.",
                            "Mit dem Namen sucht Nomi in den offiziellen Nährwerten des Anbieters statt in einem allgemeinen Rezept.",
                        ),
                    )
                },
            )
        }
        if (state.notes.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.notes.forEach { note ->
                        Text(
                            text = "• $note",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { MealCategorySelector(state.mealCategory, onMealCategoryChanged) }
        item {
            Button(
                onClick = onConfirm,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(nomiString("Find nutrition", "Nährwerte suchen"))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingContent(
    stage: AiProcessingStage,
    sourceUrls: List<String>,
    modifier: Modifier = Modifier,
) {
    val stages = AiProcessingStage.entries
    val currentIndex = stages.indexOf(stage)
    val showResearchSources = stage == AiProcessingStage.FINDING_NUTRITION || sourceUrls.isNotEmpty()
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator()
        Spacer(Modifier.height(28.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (showResearchSources) {
                AnimatedWebsiteIconStack(sourceUrls = sourceUrls)
                Spacer(Modifier.width(12.dp))
            }
            Text(stage.label(), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { (currentIndex + 1f) / stages.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            nomiString(
                "Nomi is turning your description into editable foods.",
                "Nomi verwandelt deine Beschreibung in bearbeitbare Lebensmittel.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewContent(
    analysis: FoodAnalysis,
    mealCategory: MealCategory,
    onMealCategoryChanged: (MealCategory) -> Unit,
    onEditItem: (Int) -> Unit,
    onChangePortion: (Int) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = analysis.items.sumOf(AnalyzedFoodItem::calories)
    val locale = nomiLocale()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(nomiString("Detected", "Erkannt"), style = MaterialTheme.typography.headlineMedium)
                Text(
                    nomiString("Check the portions before saving.", "Prüfe die Portionen vor dem Speichern."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MealCategorySelector(mealCategory, onMealCategoryChanged)
            }
        }
        itemsIndexed(
            items = analysis.items,
            key = { index, item -> "${index}-${item.name}" },
        ) { index, item ->
            val quantityDisplay = item.quantityDisplay(locale)
            ListItem(
                headlineContent = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                leadingContent = if (!item.sourceName.isNullOrBlank() || !item.sourceUrl.isNullOrBlank()) {
                    {
                        WebsiteFavicon(
                            sourceUrl = item.sourceUrl,
                            size = 36.dp,
                        )
                    }
                } else {
                    null
                },
                supportingContent = {
                    Column {
                        Text(quantityDisplay.withContext)
                        quantityDisplay.sourceConflictNote?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        if (item.isEstimate) {
                            Text(nomiString("Estimated", "Geschätzt"), color = MaterialTheme.colorScheme.tertiary)
                        }
                        item.sourceName?.let {
                            Text(nomiString("Source: $it", "Quelle: $it"))
                        }
                    }
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${item.calories.roundToInt()} kcal")
                        Row {
                            IconButton(onClick = { onChangePortion(index) }) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = nomiString("Change ${item.name} portion with AI", "Portion von ${item.name} mit KI ändern"),
                                )
                            }
                            IconButton(onClick = { onEditItem(index) }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = nomiString("Edit ${item.name} manually", "${item.name} manuell bearbeiten"),
                                )
                            }
                        }
                    }
                },
            )
            HorizontalDivider()
        }
        item {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(nomiString("Total", "Gesamt"), style = MaterialTheme.typography.titleLarge)
                    Text("${total.roundToInt()} kcal", style = MaterialTheme.typography.titleLarge)
                }
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(nomiString("Add to today", "Zu Heute hinzufügen"))
                }
                Text(
                    nomiString(
                        "AI and portion values can be estimates. You can edit every item.",
                        "KI- und Portionswerte können Schätzungen sein. Du kannst jeden Eintrag bearbeiten.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    state: FoodLoggingUiState.Error,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Restaurant, contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text(state.message, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        if (state.canRetry) {
            Button(onClick = onRetry) { Text(nomiString("Try again", "Erneut versuchen")) }
        }
        FilledTonalButton(onClick = onManual) {
            Text(nomiString("Enter manually", "Manuell eingeben"))
        }
    }
}

@Composable
private fun ManualFoodContent(
    draft: ManualFoodDraft,
    onDraftChanged: (ManualFoodDraft) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                nomiString("Enter food manually", "Lebensmittel manuell eingeben"),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item { ManualTextField(nomiString("Name", "Name"), draft.name, { onDraftChanged(draft.copy(name = it)) }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ManualTextField(
                    nomiString("Amount", "Menge"),
                    draft.amount,
                    { onDraftChanged(draft.copy(amount = it)) },
                    Modifier.weight(1f),
                    numeric = true,
                )
                ManualTextField(
                    nomiString("Unit", "Einheit"),
                    draft.unit,
                    { onDraftChanged(draft.copy(unit = it)) },
                    Modifier.weight(1f),
                )
            }
        }
        item { ManualTextField(nomiString("Calories", "Kalorien"), draft.calories, { onDraftChanged(draft.copy(calories = it)) }, numeric = true) }
        item { ManualTextField(nomiString("Protein (g)", "Eiweiß (g)"), draft.protein, { onDraftChanged(draft.copy(protein = it)) }, numeric = true) }
        item { ManualTextField(nomiString("Carbohydrates (g)", "Kohlenhydrate (g)"), draft.carbohydrates, { onDraftChanged(draft.copy(carbohydrates = it)) }, numeric = true) }
        item { ManualTextField(nomiString("Fat (g)", "Fett (g)"), draft.fat, { onDraftChanged(draft.copy(fat = it)) }, numeric = true) }
        item { MealCategorySelector(draft.mealCategory) { onDraftChanged(draft.copy(mealCategory = it)) } }
        item {
            Button(
                onClick = onConfirm,
                enabled = draft.isValid,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            ) {
                Text(nomiString("Add food", "Essen hinzufügen"))
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ManualTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun MealCategorySelector(
    selected: MealCategory,
    onSelected: (MealCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(nomiString("Meal", "Mahlzeit"), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MealCategory.entries.forEachIndexed { index, category ->
                SegmentedButton(
                    selected = selected == category,
                    onClick = { onSelected(category) },
                    shape = SegmentedButtonDefaults.itemShape(index, MealCategory.entries.size),
                    label = { Text(category.localizedDisplayName().take(1)) },
                    icon = {},
                )
            }
        }
        Text(selected.localizedDisplayName(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AiProcessingStage.label(): String = when (this) {
    AiProcessingStage.UNDERSTANDING_MEAL -> nomiString("Understanding your meal", "Deine Mahlzeit wird verstanden")
    AiProcessingStage.FINDING_NUTRITION -> nomiString("Finding nutrition information", "Nährwertinformationen werden gesucht")
    AiProcessingStage.CHECKING_PORTIONS -> nomiString("Checking portions", "Portionen werden geprüft")
    AiProcessingStage.PUTTING_IT_TOGETHER -> nomiString("Putting it together", "Alles wird zusammengestellt")
}

@Composable
private fun MealCategory.localizedDisplayName(): String = when (this) {
    MealCategory.BREAKFAST -> nomiString("Breakfast", "Frühstück")
    MealCategory.LUNCH -> nomiString("Lunch", "Mittagessen")
    MealCategory.DINNER -> nomiString("Dinner", "Abendessen")
    MealCategory.SNACKS -> nomiString("Snacks", "Snacks")
}

@Composable
private fun Double.clean(): String = if (this % 1.0 == 0.0) {
    roundToInt().toString()
} else {
    String.format(nomiLocale(), "%.1f", this)
}
