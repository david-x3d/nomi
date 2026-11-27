package com.nomi.app.ui.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.ai.model.AiProcessingStage
import com.nomi.app.data.local.entity.PendingFoodLogEntity
import com.nomi.app.integration.logging.PendingLoggingStatus
import com.nomi.app.ui.components.AnimatedWebsiteIconStack
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.components.nomiCardBorder
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString

@Composable
internal fun OfflineQueueBanner(
    isOnline: Boolean,
    queuedCount: Int,
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = queuedCount > 0 && !isOnline,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = nomiFormat(
                        "Offline · {0} queued to log automatically when reconnected",
                        queuedCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onRetryAll) {
                    Text(nomiString("Retry"))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PendingMealCard(
    entry: PendingFoodLogEntity,
    onRetry: (Long) -> Unit,
    onEdit: (PendingFoodLogEntity) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (entry.status) {
        PendingLoggingStatus.PROCESSING -> ProcessingPendingCard(entry, onCancel, modifier)
        PendingLoggingStatus.QUEUED_OFFLINE -> QueuedOfflineCard(entry, onRetry, onEdit, onCancel, modifier)
        else -> FailedPendingCard(entry, onRetry, onEdit, onCancel, modifier)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProcessingPendingCard(
    entry: PendingFoodLogEntity,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stage = entry.stageLabel?.let { label ->
        AiProcessingStage.entries.firstOrNull { it.name == label }
    } ?: AiProcessingStage.UNDERSTANDING_MEAL

    MealProcessingCard(
        description = entry.inputText,
        stage = stage,
        onCancel = { onCancel(entry.id) },
        modifier = modifier,
        runsInBackground = true,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MealProcessingCard(
    description: String,
    stage: AiProcessingStage,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    runsInBackground: Boolean = false,
    onEditText: (() -> Unit)? = null,
    sourceUrls: List<String> = emptyList(),
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = nomiCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = MaterialShapes.SoftBurst.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(modifier = Modifier.padding(9.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = description.ifBlank { nomiString("Understanding your meal") },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stage.localizedPendingLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = nomiString("Cancel"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            if (runsInBackground) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = nomiString("Finishes in the background if you leave Nomi"),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (onEditText != null || sourceUrls.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (onEditText != null) {
                        TextButton(onClick = onEditText) { Text(nomiString("Edit text")) }
                    }
                    if (sourceUrls.isNotEmpty() || stage == AiProcessingStage.FINDING_NUTRITION) {
                        AnimatedWebsiteIconStack(sourceUrls = sourceUrls)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueuedOfflineCard(
    entry: PendingFoodLogEntity,
    onRetry: (Long) -> Unit,
    onEdit: (PendingFoodLogEntity) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = nomiCardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = MaterialShapes.Clover4Leaf.toShape(),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Box(modifier = Modifier.padding(9.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = entry.inputText,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nomiString("Queued offline · logs when back online"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onRetry(entry.id) }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = nomiString("Retry"),
                        modifier = Modifier.size(19.dp),
                    )
                }
                IconButton(onClick = { onEdit(entry) }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = nomiString("Edit"),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = { onCancel(entry.id) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = nomiString("Remove"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FailedPendingCard(
    entry: PendingFoodLogEntity,
    onRetry: (Long) -> Unit,
    onEdit: (PendingFoodLogEntity) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = nomiCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = MaterialShapes.Cookie4Sided.toShape(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Box(modifier = Modifier.padding(9.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.inputText,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.errorMessage ?: nomiString("Could not log this meal"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { onRetry(entry.id) },
                    shape = NomiShapes.Action,
                ) {
                    Text(nomiString("Retry"))
                }
                TextButton(onClick = { onEdit(entry) }) {
                    Text(nomiString("Edit"))
                }
                TextButton(onClick = { onCancel(entry.id) }) {
                    Text(nomiString("Dismiss"))
                }
            }
        }
    }
}

@Composable
private fun AiProcessingStage.localizedPendingLabel(): String = when (this) {
    AiProcessingStage.UNDERSTANDING_MEAL -> nomiString("Understanding meal…")
    AiProcessingStage.FINDING_NUTRITION -> nomiString("Researching nutrition…")
    AiProcessingStage.CHECKING_PORTIONS -> nomiString("Checking portions…")
    AiProcessingStage.PUTTING_IT_TOGETHER -> nomiString("Putting it together…")
}
