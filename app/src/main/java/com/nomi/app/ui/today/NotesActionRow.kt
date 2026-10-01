package com.nomi.app.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.capture.InlineDictationState
import com.nomi.app.ui.components.DictationWaveform
import com.nomi.app.ui.components.NomiIcons
import com.nomi.app.ui.components.hairlineOnPitchBlack
import com.nomi.app.ui.feedback.nomiPress
import com.nomi.app.ui.feedback.rememberNomiPressFeedback
import com.nomi.app.ui.localization.nomiLocale
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.share.LocalNomiShareCoordinator
import com.nomi.app.ui.share.ShareMenuSection
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiProgressMotionSpec
import kotlin.math.roundToInt

/*
 * The floating row under the page: today's calories in a pill, and the ways to add food. While
 * Nomi is listening the same row is the dictation.
 */

/**
 * The resting state of the page: one floating row holding today's calories and the ways
 * to add food. Nothing else competes with the writing surface above it.
 *
 * While Nomi is listening the same row is the dictation: the calorie pill becomes the waveform
 * and the two actions become "done" and "forget it". Nothing opens on top of the day, because
 * the day is what the sentence is about.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotesFloatingActionRow(
    state: TodayUiState,
    dictation: InlineDictationState,
    onGoals: () -> Unit,
    onVoice: () -> Unit,
    onDictationDone: () -> Unit,
    onCameraMethod: (AddFoodMethod) -> Unit,
    onChoosePhoto: () -> Unit,
    onLibraryMethod: (AddFoodMethod) -> Unit,
) {
    val locale = nomiLocale()
    var showCameraMenu by rememberSaveable { mutableStateOf(false) }
    var showLibraryMenu by rememberSaveable { mutableStateOf(false) }
    val shareCoordinator = LocalNomiShareCoordinator.current
    fun closeLibraryMenu() {
        showLibraryMenu = false
        shareCoordinator.closeMenu()
    }
    LaunchedEffect(state.date) { closeLibraryMenu() }

    val effectsSpec = nomiFadeMotionSpec<Float>()
    // Today's calories settle into their new value instead of snapping when an entry
    // is added, removed, or rescaled.
    val animatedCalories by animateFloatAsState(
        targetValue = state.caloriesConsumed.toFloat(),
        animationSpec = nomiProgressMotionSpec(),
        label = "calories consumed",
    )
    val calorieValue = animatedCalories.roundToInt().formatted(locale)
    AnimatedContent(
        targetState = dictation.isActive,
        modifier = Modifier
            .widthIn(max = 760.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        transitionSpec = {
            (fadeIn(animationSpec = effectsSpec) +
                scaleIn(animationSpec = effectsSpec, initialScale = 0.94f))
                .togetherWith(
                    fadeOut(animationSpec = effectsSpec) +
                        scaleOut(animationSpec = effectsSpec, targetScale = 0.94f),
                )
        },
        label = "dictation row",
    ) { listening ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = lerp(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.colorScheme.primaryContainer,
                0.08f,
            ),
            border = hairlineOnPitchBlack(),
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (listening) {
                    NotesPill(modifier = Modifier.weight(1f)) {
                        DictationPillContent(dictation)
                    }
                    NotesCircleAction(
                        icon = Icons.Default.Check,
                        description = nomiString("Done speaking"),
                        onClick = onDictationDone,
                        emphasized = true,
                    )
                    NotesCircleAction(
                        icon = Icons.Default.Close,
                        description = nomiString("Discard dictation"),
                        onClick = dictation.cancel,
                    )
                } else {
                    val burnedCalories = state.effectiveBurnedCaloriesKcal
                    NotesPill(
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {},
                        onClick = onGoals,
                        // The pair needs every millimetre it can get beside the three round
                        // actions, so the pill keeps a little less air around it than it does
                        // when it holds a single figure or the waveform.
                        contentPadding = if (burnedCalories == null) 18.dp else 14.dp,
                    ) {
                        // Eaten and burned are the same unit, so "kcal" is written once, after
                        // the pair. Spelled out twice it did not fit, and the burned figure was
                        // always the one clipped off the end. FlowRow keeps that promise when
                        // the text is scaled up: the burned figure drops onto its own line
                        // instead of being cut in half.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            // Centred against each other rather than hung from the top of the
                            // line, so the two icons sit on one axis whatever the numbers do.
                            verticalArrangement = Arrangement.spacedBy(
                                space = 2.dp,
                                alignment = Alignment.CenterVertically,
                            ),
                        ) {
                            CalorieFigure(
                                modifier = Modifier.align(Alignment.CenterVertically),
                                icon = NomiIcons.Flame,
                                description = nomiString("Eaten"),
                                tint = MaterialTheme.colorScheme.primary,
                                text = if (burnedCalories == null) {
                                    "$calorieValue kcal"
                                } else {
                                    calorieValue
                                },
                            )
                            // Always show Nomi's visibly approximate step estimate.
                            // It does not change the eaten figure beside it.
                            burnedCalories?.let { burned ->
                                CalorieFigure(
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                    icon = NomiIcons.Runner,
                                    description = nomiString(
                                        if (state.burnedCaloriesAreEstimated) {
                                            "Estimated from steps"
                                        } else {
                                            "Burned through activity"
                                        },
                                    ),
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    text = if (state.burnedCaloriesAreEstimated) {
                                        "${estimatedStepCaloriesValue(burned, locale)} kcal"
                                    } else {
                                        "${burned.roundToInt().formatted(locale)} kcal"
                                    },
                                )
                            }
                        }
                    }
                    NotesCircleAction(
                        icon = Icons.Default.Mic,
                        description = nomiString("Describe food by voice"),
                        onClick = onVoice,
                    )
                    Box {
                        NotesCircleAction(
                            icon = Icons.Default.CameraAlt,
                            description = nomiString("Photo"),
                            onClick = {
                                closeLibraryMenu()
                                showCameraMenu = true
                            },
                        )
                        DropdownMenu(
                            expanded = showCameraMenu,
                            onDismissRequest = { showCameraMenu = false },
                            offset = DpOffset(x = (-8).dp, y = (-8).dp),
                            shape = RoundedCornerShape(24.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            CompactActionMenuItem(
                                icon = Icons.Default.CameraAlt,
                                label = nomiString("Photo"),
                                onClick = {
                                    showCameraMenu = false
                                    onCameraMethod(AddFoodMethod.PHOTO)
                                },
                            )
                            CompactActionMenuItem(
                                icon = Icons.Default.QrCodeScanner,
                                label = nomiString("Barcode"),
                                onClick = {
                                    showCameraMenu = false
                                    onCameraMethod(AddFoodMethod.BARCODE)
                                },
                            )
                            CompactActionMenuItem(
                                icon = Icons.Default.RestaurantMenu,
                                label = nomiString("Scan menu"),
                                onClick = {
                                    showCameraMenu = false
                                    onCameraMethod(AddFoodMethod.MENU)
                                },
                            )
                            CompactActionMenuItem(
                                icon = Icons.Default.PhotoLibrary,
                                label = nomiString("Choose a photo"),
                                onClick = {
                                    showCameraMenu = false
                                    onChoosePhoto()
                                },
                            )
                        }
                    }
                    Box {
                        NotesCircleAction(
                            icon = Icons.Default.Add,
                            description = nomiString("More ways to add food"),
                            onClick = {
                                showCameraMenu = false
                                showLibraryMenu = true
                            },
                        )
                        DropdownMenu(
                            expanded = showLibraryMenu,
                            onDismissRequest = { closeLibraryMenu() },
                            offset = DpOffset(x = (-8).dp, y = (-8).dp),
                            shape = RoundedCornerShape(24.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            ShareMenuSection(
                                dayEntries = state.entries,
                                day = state.date,
                                onFinished = { closeLibraryMenu() },
                            )
                            if (shareCoordinator.stage == com.nomi.app.ui.share.NomiShareStage.Collapsed) {
                                HorizontalDivider()
                                CompactActionMenuItem(
                                    icon = Icons.Default.History,
                                    label = nomiString("Recent"),
                                    onClick = {
                                        closeLibraryMenu()
                                        onLibraryMethod(AddFoodMethod.RECENT)
                                    },
                                )
                                CompactActionMenuItem(
                                    icon = Icons.Default.FavoriteBorder,
                                    label = nomiString("Favorites"),
                                    onClick = {
                                        closeLibraryMenu()
                                        onLibraryMethod(AddFoodMethod.FAVORITES)
                                    },
                                )
                                CompactActionMenuItem(
                                    icon = Icons.Default.RestaurantMenu,
                                    label = nomiString("Saved meals"),
                                    onClick = {
                                        closeLibraryMenu()
                                        onLibraryMethod(AddFoodMethod.SAVED_MEALS)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactActionMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null) },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
    )
}

/**
 * One figure in the calorie pill: an icon, its number, and nothing else.
 *
 * Eaten and burned are set in the same type and the same colour and read as two figures of
 * equal standing. Only the icon beside each number says which is which.
 */
@Composable
private fun CalorieFigure(
    icon: ImageVector,
    description: String,
    tint: Color,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            // Both icons keep the same size and sit on the same line: the quieter figure is
            // told apart by its type and colour, not by a smaller symbol.
            modifier = Modifier.size(18.dp),
            tint = tint,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The rounded slot on the left of the floating row, whatever happens to be inside it. */
@Composable
private fun NotesPill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    val shape = CircleShape
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    val border = hairlineOnPitchBlack()
    val inner: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = contentPadding, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
    }
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = color, border = border) { inner() }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
        ) { inner() }
    }
}

/**
 * What the pill says while it is the microphone: the waveform when there is something to hear,
 * and words only when there is something the bars cannot say.
 */
@Composable
private fun DictationPillContent(dictation: InlineDictationState) {
    val progress = dictation.downloadProgress
    when {
        dictation.message != null -> Text(
            text = dictation.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        progress != null -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = nomiString("Preparing speech, once only…"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        dictation.isTranscribing -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = nomiString("Writing it down…"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        else -> DictationWaveform(
            level = dictation.level,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesCircleAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val press = rememberNomiPressFeedback()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        Surface(
            onClick = onClick,
            interactionSource = press.interactionSource,
            shape = CircleShape,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (emphasized) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            border = if (emphasized) null else hairlineOnPitchBlack(),
            modifier = Modifier.nomiPress(press),
        ) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = description)
            }
        }
    }
}
