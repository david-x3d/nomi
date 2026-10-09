package com.nomi.wear.ui

import android.app.RemoteInput
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.nomi.wear.LogStatus
import com.nomi.wear.QuickItem
import com.nomi.wear.R
import com.nomi.wear.TodayState
import com.nomi.wear.WatchToday
import com.nomi.wear.WatchViewModel
import com.nomi.wear.WearContract
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HOME = "home"
private const val QUICK_ADD = "quick"
private const val VOICE_RESULT_KEY = "meal"

@Composable
fun NomiWatchApp(viewModel: WatchViewModel) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val voiceRequested by viewModel.voiceRequested.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()

    NomiWatchTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = navController, startDestination = HOME) {
                composable(HOME) {
                    TodayScreen(
                        state = today,
                        waiting = status == LogStatus.Waiting,
                        voiceRequested = voiceRequested,
                        onVoiceRequestHandled = viewModel::consumeVoiceRequest,
                        onLogText = viewModel::logText,
                        onOpenQuickAdd = { navController.navigate(QUICK_ADD) },
                        onRefresh = viewModel::refresh,
                    )
                }
                composable(QUICK_ADD) {
                    QuickAddScreen(
                        items = (today as? TodayState.Ready)?.today?.quickItems.orEmpty(),
                        onSelect = { item ->
                            viewModel.logQuick(item)
                            navController.popBackStack()
                        },
                    )
                }
            }
            StatusDialogs(status, onDismiss = viewModel::dismissStatus)
        }
    }
}

@Composable
private fun TodayScreen(
    state: TodayState,
    waiting: Boolean,
    voiceRequested: Boolean,
    onVoiceRequestHandled: () -> Unit,
    onLogText: (String) -> Unit,
    onOpenQuickAdd: () -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val prompt = stringResource(R.string.voice_prompt)
    val voiceInput = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(VOICE_RESULT_KEY)?.toString()
        if (!text.isNullOrBlank()) onLogText(text)
    }
    val ready = (state as? TodayState.Ready)?.today?.takeIf { it.hasProfile }
    fun startVoiceInput() {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val input = RemoteInput.Builder(VOICE_RESULT_KEY)
            .setLabel(prompt)
            .wearableExtender {
                setEmojisAllowed(false)
                setInputActionType(EditorInfo.IME_ACTION_DONE)
            }
            .build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
        voiceInput.launch(intent)
    }
    LaunchedEffect(voiceRequested, ready != null) {
        if (voiceRequested && ready != null) {
            onVoiceRequestHandled()
            if (!waiting) startVoiceInput()
        }
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            if (ready != null) {
                EdgeButton(
                    onClick = ::startVoiceInput,
                    enabled = !waiting,
                    buttonSize = EdgeButtonSize.Medium,
                ) {
                    Icon(painterResource(R.drawable.ic_mic), contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.log_meal))
                }
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.today)) }
            }
            when {
                state == TodayState.Loading -> item { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }
                state == TodayState.NotConnected || state is TodayState.Ready && !state.today.hasProfile -> {
                    item {
                        Text(
                            text = stringResource(
                                if (state == TodayState.NotConnected) R.string.not_connected else R.string.not_set_up,
                            ),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                    }
                    item {
                        FilledTonalButton(
                            onClick = onRefresh,
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
                            label = { Text(stringResource(R.string.try_again)) },
                        )
                    }
                }
                ready != null -> {
                    if (waiting) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.waiting), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    item { CalorieSummary(ready) }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                        ) { Macros(ready) }
                    }
                    item {
                        FilledTonalButton(
                            onClick = onOpenQuickAdd,
                            modifier = Modifier.fillMaxWidth(),
                            icon = { Icon(painterResource(R.drawable.ic_star), contentDescription = null) },
                            label = { Text(stringResource(R.string.quick_add)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalorieSummary(today: WatchToday) {
    val number = integerFormat()
    val remaining = today.remainingKcal
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(
            text = number.format(remaining?.let(::abs) ?: today.caloriesKcal.roundToInt()),
            style = MaterialTheme.typography.numeralMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(
                when {
                    remaining == null -> R.string.kcal_eaten
                    remaining < 0 -> R.string.kcal_over
                    else -> R.string.kcal_left
                },
            ),
            style = MaterialTheme.typography.labelMedium,
        )
        if (today.calorieTargetKcal != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { today.calorieProgress },
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        }
    }
}

@Composable
private fun Macros(today: WatchToday) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        MacroRow(stringResource(R.string.macro_protein), today.proteinGrams, today.proteinTargetGrams, MaterialTheme.colorScheme.secondary)
        MacroRow(stringResource(R.string.macro_carbs), today.carbohydrateGrams, today.carbohydrateTargetGrams, MaterialTheme.colorScheme.tertiary)
        MacroRow(stringResource(R.string.macro_fat), today.fatGrams, today.fatTargetGrams, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MacroRow(label: String, grams: Double, target: Double?, color: Color) {
    val number = integerFormat()
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = if (target != null) {
                    stringResource(R.string.grams_of_target, number.format(grams.roundToInt()), number.format(target.roundToInt()))
                } else {
                    stringResource(R.string.grams, number.format(grams.roundToInt()))
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (target != null) {
            LinearProgressIndicator(
                progress = { WatchToday.progress(grams, target) },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                colors = ProgressIndicatorDefaults.colors(indicatorColor = color),
            )
        }
    }
}

@Composable
private fun QuickAddScreen(items: List<QuickItem>, onSelect: (QuickItem) -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val number = integerFormat()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.quick_add)) }
            }
            if (items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.quick_add_empty),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
            items(items.size, key = { index -> "${items[index].kind}:${items[index].id}" }) { index ->
                val item = items[index]
                val kcal = stringResource(R.string.kcal_value, number.format(item.caloriesKcal.roundToInt()))
                Button(
                    onClick = { onSelect(item) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = {
                        Icon(
                            painterResource(
                                if (item.kind == WearContract.QUICK_SAVED_MEAL) R.drawable.ic_restaurant else R.drawable.ic_star,
                            ),
                            contentDescription = null,
                        )
                    },
                    label = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        Text(
                            text = listOf(item.subtitle, kcal).filter { it.isNotBlank() }.joinToString(" · "),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusDialogs(status: LogStatus, onDismiss: () -> Unit) {
    val curvedStyle = ConfirmationDialogDefaults.curvedTextStyle
    val done = status as? LogStatus.Done
    val number = integerFormat()

    val loggedText = done?.result?.takeIf { it.ok }?.let { result ->
        if (result.addedKcal >= 1.0) {
            stringResource(R.string.logged_kcal, number.format(result.addedKcal.roundToInt()))
        } else {
            stringResource(R.string.logged)
        }
    }
    SuccessConfirmationDialog(
        visible = loggedText != null,
        onDismissRequest = onDismiss,
        curvedText = loggedText?.let { text -> { confirmationDialogCurvedText(text, curvedStyle) } },
    )

    val unreachableText = stringResource(R.string.phone_unreachable)
    FailureConfirmationDialog(
        visible = status == LogStatus.PhoneUnreachable,
        onDismissRequest = onDismiss,
        curvedText = { confirmationDialogCurvedText(unreachableText, curvedStyle) },
    )

    // The phone's reason can be a whole sentence, such as a missing key in Settings, which does
    // not fit on a curve, so a failed log is shown as a dialog the user reads and closes.
    val failure = done?.result?.takeIf { !it.ok }
    AlertDialog(
        visible = failure != null,
        onDismissRequest = onDismiss,
        edgeButton = { AlertDialogDefaults.EdgeButton(onClick = onDismiss) },
        title = { Text(stringResource(R.string.log_failed)) },
        text = failure?.message?.takeIf { it.isNotBlank() }?.let { message -> { Text(message, textAlign = TextAlign.Center) } },
    )
}

@Composable
private fun integerFormat(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}
