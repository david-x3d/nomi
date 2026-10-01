package com.nomi.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AnalyzedFoodItem
import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.ai.model.FoodAnalysis
import com.nomi.app.ui.components.AnimatedWebsiteIconStack
import com.nomi.app.ui.components.NomiTextField
import com.nomi.app.ui.components.hairlineOnPitchBlack
import com.nomi.app.ui.feedback.nomiPress
import com.nomi.app.ui.feedback.rememberNomiPressFeedback
import com.nomi.app.ui.format.quantityDisplay
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.logging.FoodLoggingUiState
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiLayoutMotionSpec
import com.nomi.app.ui.theme.animationsAreDisabled
import com.nomi.app.ui.theme.nomiPageMotionSpec
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * What the writing surface at the bottom of the Today page shows while an entry is on its way:
 * the composer, the research spinner, and the preview, photo-review, error and manual-entry notes.
 */

@Composable
internal fun InlineLoggingState(
    state: FoodLoggingUiState,
    rememberedDescription: String,
    suppressComposer: Boolean,
    composerFocused: Boolean,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onEditText: () -> Unit,
    onEditPreview: () -> Unit,
    onDismissDraft: () -> Unit,
    onPhotoDescriptionChanged: (String) -> Unit,
    onPhotoPlaceChanged: (String) -> Unit,
    onConfirmPhotoDescription: () -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxWidth(),
        contentKey = { it::class },
        transitionSpec = {
            (
                fadeIn(animationSpec = nomiFadeMotionSpec()) +
                    slideInVertically(animationSpec = nomiPageMotionSpec()) { height -> height / 24 }
                ).togetherWith(
                fadeOut(animationSpec = nomiFadeMotionSpec()) +
                    slideOutVertically(animationSpec = nomiPageMotionSpec()) { height -> -height / 30 },
            ).using(
                SizeTransform(
                    clip = false,
                    sizeAnimationSpec = { _, _ -> nomiLayoutMotionSpec() },
                ),
            )
        },
        contentAlignment = Alignment.TopCenter,
        label = "Food logging state",
    ) { animatedState ->
        when (animatedState) {
            is FoodLoggingUiState.Input -> if (!suppressComposer) {
                InlineComposerCanvas(
                    text = animatedState.text,
                    autoFocus = composerFocused,
                    fillsPage = true,
                    onTextChanged = onTextChanged,
                    onAnalyze = onAnalyze,
                )
            }
            is FoodLoggingUiState.Processing -> ProcessingNote(
                description = rememberedDescription,
                stage = animatedState.stage,
                sourceUrls = animatedState.sourceUrls,
                onEditText = onEditText,
                onCancel = onDismissDraft,
            )

            is FoodLoggingUiState.PhotoReview -> PhotoReviewNote(
                state = animatedState,
                onDescriptionChanged = onPhotoDescriptionChanged,
                onPlaceChanged = onPhotoPlaceChanged,
                onConfirm = onConfirmPhotoDescription,
                onCancel = onDismissDraft,
            )

            is FoodLoggingUiState.Preview -> PreviewNote(
                analysis = animatedState.analysis,
                description = rememberedDescription,
                onAdd = onConfirm,
                onEditText = onEditText,
                onEditPreview = onEditPreview,
                onCancel = onDismissDraft,
            )

            is FoodLoggingUiState.Error -> ErrorNote(
                description = rememberedDescription,
                message = animatedState.message,
                canRetry = animatedState.canRetry,
                onRetry = onRetry,
                onEditText = onEditText,
                onCancel = onDismissDraft,
            )

            is FoodLoggingUiState.Manual -> ManualDraftNote(
                state = animatedState,
                onAdd = onConfirm,
                onEdit = onEditPreview,
                onCancel = onDismissDraft,
            )
        }
    }
}

/**
 * The surface you write on.
 *
 * It is deliberately not a text field in appearance: same typography, padding, and alignment as
 * a logged row, so what you type reads as part of the page and stays in place when it turns
 * into real entries. As the page composer it claims the rest of the sheet and takes as many
 * lines as you want - Return breaks a line instead of submitting, so several foods can be
 * written out before research starts automatically. Rewriting one existing row keeps the same
 * compact shape because there it sits between other entries.
 *
 * [initialCaret] is where the words were touched. It only seeds the caret; from then on the
 * caret belongs to the field, so typing in the middle of a sentence stays where it is.
 */
@Composable
internal fun InlineComposerCanvas(
    text: String,
    autoFocus: Boolean,
    fillsPage: Boolean,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    initialCaret: Int? = null,
    onEmptied: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }
    var typed by remember {
        val caret = (initialCaret ?: text.length).coerceIn(0, text.length)
        mutableStateOf(TextFieldValue(text, TextRange(caret)))
    }
    var userHasTyped by remember { mutableStateOf(false) }
    // Text can also change from outside - a draft reopened for correction, a cleared page -
    // and then the field follows it with the caret at the end.
    val value = if (typed.text == text) typed else TextFieldValue(text, TextRange(text.length))
    // A pause means the sentence is finished. The flag matters for reopened rows: merely
    // placing the caret must never research the unchanged entry after 1.5 seconds.
    LaunchedEffect(value.text, userHasTyped) {
        if (userHasTyped && value.text.isNotBlank()) {
            delay(AUTO_ANALYZE_DELAY_MILLIS)
            onAnalyze()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = if (fillsPage) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { updated ->
                typed = updated
                if (updated.text != text) {
                    userHasTyped = true
                    if (updated.text.isBlank() && text.isNotBlank() && onEmptied != null) {
                        onEmptied()
                    } else {
                        onTextChanged(updated.text)
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .then(if (fillsPage) Modifier.heightIn(min = 320.dp) else Modifier)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = nomiString("Tell Nomi what you ate"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                innerTextField()
            },
        )
        // This is the same right-hand position where the finished row will show its calories.
        // The dots leave with the composer when the quiet-period search starts.
        AnimatedVisibility(visible = userHasTyped && text.isNotBlank()) {
            TypingDots()
        }
    }
}

@Composable
private fun ProcessingNote(
    description: String,
    stage: AiProcessingStage,
    sourceUrls: List<String>,
    onEditText: () -> Unit,
    onCancel: () -> Unit,
) {
    val stageIndex = AiProcessingStage.entries.indexOf(stage)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = description.ifBlank {
                            nomiString("Understanding your meal")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    AnimatedContent(
                        targetState = stage,
                        label = "research source status",
                    ) { currentStage ->
                        Text(
                            text = currentStage.inlineLabel(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    LinearWavyProgressIndicator(
                        progress = { (stageIndex + 1f) / AiProcessingStage.entries.size },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
                AnimatedWebsiteIconStack(
                    sourceUrls = sourceUrls,
                    maxIcons = 3,
                )
            }
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onEditText) { Text(nomiString("Edit text")) }
                TextButton(onClick = onCancel) { Text(nomiString("Cancel")) }
            }
        }
    }
}

@Composable
private fun AiProcessingStage.inlineLabel(): String = when (this) {
    AiProcessingStage.UNDERSTANDING_MEAL -> nomiString("Understanding your meal")
    AiProcessingStage.FINDING_NUTRITION -> nomiString("Finding nutrition information")
    AiProcessingStage.CHECKING_PORTIONS -> nomiString("Checking portions")
    AiProcessingStage.PUTTING_IT_TOGETHER -> nomiString("Putting it together")
}

@Composable
private fun PreviewNote(
    analysis: FoodAnalysis,
    description: String,
    onAdd: () -> Unit,
    onEditText: () -> Unit,
    onEditPreview: () -> Unit,
    onCancel: () -> Unit,
) {
    val addPress = rememberNomiPressFeedback()
    val totalCalories = analysis.items.sumOf(AnalyzedFoodItem::calories)
    val totalProtein = analysis.items.sumOf(AnalyzedFoodItem::proteinGrams)
    val totalCarbohydrates = analysis.items.sumOf(AnalyzedFoodItem::carbohydrateGrams)
    val totalFat = analysis.items.sumOf(AnalyzedFoodItem::fatGrams)
    val locale = nomiLocale()
    val estimatedSuffix = nomiString(" · estimated")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = description.ifBlank {
                            analysis.items.joinToString { it.name }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(
                        onClick = onEditText,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(nomiString("Change wording"))
                    }
                }
                Text(
                    text = "${totalCalories.roundToInt()} kcal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

            analysis.items.forEach { item ->
                val quantityDisplay = item.quantityDisplay(locale)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = quantityDisplay.withContext +
                                if (item.isEstimate) estimatedSuffix else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        quantityDisplay.sourceConflictNote?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                    Text(
                        text = "${item.calories.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = nomiFormat(
                        "C {0} g  ·  P {1} g  ·  F {2} g",
                        totalCarbohydrates.roundToInt(),
                        totalProtein.roundToInt(),
                        totalFat.roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel) { Text(nomiString("Cancel")) }
                    FilledTonalButton(onClick = onEditPreview) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(nomiString("Edit"), maxLines = 1)
                    }
                }
                Button(
                    onClick = onAdd,
                    interactionSource = addPress.interactionSource,
                    modifier = Modifier.fillMaxWidth().nomiPress(addPress),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(nomiString("Add"), maxLines = 1)
                }
            }
        }
    }
}

/**
 * A photo's reading, offered as words before anything is looked up.
 *
 * This is the cheap moment to disagree with the camera. Editing here costs one word; editing
 * after research means throwing away a web search and running another. The note deliberately
 * looks like the meal already written on the page, because that is what it is.
 */
@Composable
private fun PhotoReviewNote(
    state: FoodLoggingUiState.PhotoReview,
    onDescriptionChanged: (String) -> Unit,
    onPlaceChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = hairlineOnPitchBlack(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = nomiString("From your photo"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NomiTextField(
                value = state.description,
                onValueChange = onDescriptionChanged,
                singleLine = false,
                minLines = 2,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = nomiString("Describe what you ate"),
            )
            NomiTextField(
                value = state.place,
                onValueChange = onPlaceChanged,
                label = nomiString("Restaurant (optional)"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (state.canContinue) onConfirm() }),
            )
            Text(
                text = nomiString("Portion weights are estimated from the photo, using clues such as plate size. Check and adjust the amounts."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.notes.forEach { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onConfirm,
                enabled = state.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(nomiString("Find nutrition"))
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(nomiString("Discard"))
            }
        }
    }
}

@Composable
private fun ErrorNote(
    description: String,
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onEditText: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = description.ifBlank {
                            nomiString("Couldn’t understand this meal")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canRetry) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = nomiString("Retry"),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = nomiString("Cancel"),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    FilledTonalButton(
                        onClick = onEditText,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = nomiString("Edit"),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualDraftNote(
    state: FoodLoggingUiState.Manual,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    val draft = state.draft
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = draft.name.ifBlank { nomiString("Manual food") },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                draft.calories.toDoubleOrNull()?.let {
                    Text(
                        text = "${it.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onCancel) { Text(nomiString("Cancel")) }
                FilledTonalButton(onClick = onEdit) { Text(nomiString("Edit")) }
                Button(onClick = onAdd, enabled = draft.isValid) {
                    Text(nomiString("Add"))
                }
            }
        }
    }
}

/** Three quiet rising dots replace the calorie total while a sentence is being written. */
@Composable
private fun TypingDots() {
    val dots = remember { List(3) { Animatable(0f) } }
    // An endless loop is the one animation that cannot be justified as motion the user asked for,
    // so it stops entirely when the system animation scale is zero. The dots then simply rest at
    // their resting value, which still communicates "waiting" without moving.
    val animate = !animationsAreDisabled()
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        dots.forEachIndexed { index, dot ->
            launch {
                delay(index * 120L)
                while (true) {
                    dot.animateTo(-5f, animationSpec = tween(260))
                    dot.animateTo(0f, animationSpec = tween(260))
                    delay(220L)
                }
            }
        }
    }
    Row(
        modifier = Modifier.semantics { contentDescription = "Nomi is waiting for you to finish typing" },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dots.forEach { dot ->
            Box(
                modifier = Modifier
                    .graphicsLayer { translationY = dot.value }
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private const val AUTO_ANALYZE_DELAY_MILLIS = 1_500L
