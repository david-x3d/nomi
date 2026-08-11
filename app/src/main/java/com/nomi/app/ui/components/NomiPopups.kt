package com.nomi.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * The shapes every popup in Nomi is cut from.
 *
 * Material 3 Expressive says the same thing about corners that it says about motion: the bigger
 * the surface, the softer it should land. Dialogs and sheets were drawing Material's baseline
 * 28 dp and 16 dp radii, which read as the default rather than as a decision, so the sizes here
 * step up together and every popup inherits them from one place.
 */
object NomiShapes {
    /** Extra-large-increased: the expressive radius for a floating dialog. */
    val Dialog = RoundedCornerShape(32.dp)

    /** Sheets only round where they leave the screen edge. */
    val Sheet = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

    /** Menus are small containers, so they take the large-increased step instead. */
    val Menu = RoundedCornerShape(20.dp)

    /** The rounded highlight a single menu row lights up with. */
    val MenuItem = RoundedCornerShape(16.dp)

    /** Full-width dialog and sheet actions are pills, not rectangles with soft corners. */
    val Action = RoundedCornerShape(percent = 50)
}

/** How tall a primary action has to be before it reads as expressive rather than compact. */
private val ActionHeight = 56.dp

/**
 * One dialog, used everywhere.
 *
 * Material's own [androidx.compose.material3.AlertDialog] puts its actions in a right-aligned row
 * of text buttons - two words of unfilled text in the corner of a large surface. Expressive asks
 * the opposite: the decision should be the heaviest thing on the surface. So the confirm action is
 * a filled pill spanning half the width, the dismiss action is its outlined twin, and an optional
 * hero icon in a tonal circle names the subject before the title does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomiDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
    onDismissAction: (() -> Unit)? = null,
    destructive: Boolean = false,
    properties: DialogProperties = DialogProperties(),
    contentSpacing: androidx.compose.ui.unit.Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(max = 560.dp),
        properties = properties,
    ) {
        Surface(
            shape = NomiShapes.Dialog,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = hairlineOnPitchBlack(),
        ) {
            Column(modifier = Modifier.padding(top = 28.dp, bottom = 20.dp)) {
                NomiDialogHeader(title = title, icon = icon, subtitle = subtitle)
                Column(
                    modifier = Modifier
                        // fill = false so a short dialog stays short; the scroll only engages
                        // once the body is taller than the window allows.
                        .weight(weight = 1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp)
                        .padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(contentSpacing),
                    content = content,
                )
                if (confirmLabel != null || dismissLabel != null) {
                    NomiDialogActions(
                        confirmLabel = confirmLabel,
                        onConfirm = onConfirm,
                        confirmEnabled = confirmEnabled,
                        dismissLabel = dismissLabel,
                        onDismiss = onDismissAction ?: onDismissRequest,
                        destructive = destructive,
                    )
                }
            }
        }
    }
}

@Composable
private fun NomiDialogHeader(title: String, icon: ImageVector?, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NomiDialogActions(
    confirmLabel: String?,
    onConfirm: (() -> Unit)?,
    confirmEnabled: Boolean,
    dismissLabel: String?,
    onDismiss: () -> Unit,
    destructive: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        dismissLabel?.let { label ->
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(ActionHeight),
                shape = NomiShapes.Action,
            ) {
                Text(label, maxLines = 1)
            }
        }
        if (confirmLabel != null && onConfirm != null) {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f).height(ActionHeight),
                shape = NomiShapes.Action,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel, maxLines = 1)
            }
        }
    }
}

/**
 * One date picker, used everywhere.
 *
 * Material's dialog is already the right component; what it does not do on its own is match the
 * radius and the action weighting the rest of Nomi's popups now use, so the three places that
 * ask for a date go through here rather than each configuring it a little differently.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NomiDatePickerDialog(
    state: DatePickerState,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    showModeToggle: Boolean = true,
) {
    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = NomiShapes.Dialog,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = state.selectedDateMillis != null,
                shape = NomiShapes.Action,
                modifier = Modifier.height(48.dp),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismissRequest,
                shape = NomiShapes.Action,
                modifier = Modifier.height(48.dp),
            ) {
                Text(dismissLabel)
            }
        },
    ) {
        DatePicker(
            state = state,
            showModeToggle = showModeToggle,
            title = title?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 24.dp, top = 20.dp),
                    )
                }
            },
        )
    }
}

/**
 * One bottom sheet, used everywhere.
 *
 * The half-open stop is skipped for the same reason everywhere: it made every sheet look like it
 * hesitated on the way up. The content rides in on the theme's own spatial spec a beat behind the
 * container, which is the expressive entrance - the surface arrives, then what is on it does.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NomiSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var contentVisible by remember { mutableStateOf(false) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(Unit) { contentVisible = true }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = NomiShapes.Sheet,
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(animationSpec = effectsSpec) +
                slideInVertically(animationSpec = spatialSpec) { height -> height / 8 },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                content = content,
            )
        }
    }
}

/**
 * The first thing on a sheet: what this is, and what it is about.
 *
 * The icon is the same tonal circle a dialog uses, so a sheet and a dialog raised by the same
 * action look like two poses of one component rather than two components.
 */
@Composable
fun NomiSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One dropdown menu, used everywhere.
 *
 * Material's default menu is a near-square surface at `surface` tone, which on Nomi's own canvas -
 * where `surface` is the page - reads as a floating rectangle of nothing. A container tone and the
 * large-increased radius put it back on the same shelf as the cards it is raised over.
 */
@Composable
fun NomiMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = NomiShapes.Menu,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = MenuDefaults.ShadowElevation,
        border = hairlineOnPitchBlack(),
        content = content,
    )
}

/**
 * A menu row that lights up as a rounded pill rather than a full-bleed rectangle, so the highlight
 * echoes the shape of the menu holding it.
 */
@Composable
fun NomiMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
) {
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .clip(NomiShapes.MenuItem),
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = contentColor,
            leadingIconColor = contentColor,
        ),
    )
}
