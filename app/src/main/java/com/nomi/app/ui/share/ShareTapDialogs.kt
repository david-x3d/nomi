package com.nomi.app.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nomi.app.data.share.ShareEnvelopeV1
import com.nomi.app.data.share.ShareFoodV1
import com.nomi.app.ui.components.NomiCard
import com.nomi.app.ui.components.NomiDialog
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The screen a user looks at while holding two phones together.
 *
 * This is the whole of the interface for a tap, and the design problem in it is that there is
 * nothing to watch. A tap has no connecting animation and no meaningful progress until the bytes
 * start moving, so the screen is mostly an instruction, stated once, with what is being sent next
 * to it. The instruction names which phone does what, because "hold them together" is ambiguous
 * and doing it backwards is the first thing anyone tries.
 */
@Composable
fun ShareSendingDialog(
    foodCount: Int,
    kcal: Double,
    onCancel: () -> Unit,
) {
    NomiDialog(
        onDismissRequest = onCancel,
        title = nomiString("Ready to share"),
        icon = Icons.Default.Nfc,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = nomiFormat(
                        "Hold the other phone to the back of this one to send {0} foods",
                        foodCount,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = nomiFormat("{0} in total", "${kcal.roundToInt()} kcal"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    // Said before the first attempt rather than after it fails, because the phone
                    // doing the reading has to be open: a locked one is deaf.
                    text = nomiString("On the other phone, open Nomi and choose Receive a shared day before touching the phones"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmLabel = nomiString("Cancel"),
        onConfirm = onCancel,
    )
}

/**
 * The listening half of a tap, and the first thing a user sees if they have never done this.
 *
 * The progress bar only appears once bytes start arriving. Before that an empty bar would claim
 * the two phones had already found each other when they had not, which is the most confusing
 * thing this screen could do.
 */
@Composable
fun ShareReceivingDialog(
    receivedBytes: Int,
    expectedBytes: Int,
    onCancel: () -> Unit,
) {
    val started = expectedBytes > 0
    NomiDialog(
        onDismissRequest = onCancel,
        title = nomiString("Waiting for a phone"),
        icon = Icons.Default.Nfc,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = nomiString(
                        "Hold the sending phone to the back of this one to receive its day",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = nomiString("Keep both phones still until the day has arrived"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (started) {
                    LinearProgressIndicator(
                        progress = { receivedBytes.toFloat() / expectedBytes },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = nomiString("Looking for a phone"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmLabel = nomiString("Cancel"),
        onConfirm = onCancel,
    )
}

/**
 * The day that arrived, shown before it goes anywhere near the diary.
 *
 * A file that has crossed a radio is not yet a fact about this phone's user. This is where they
 * see which day it is, which foods came with it, and what they add up to, and only then does it
 * become an entry. The date is shown because an arriving day keeps the day it was eaten on, and
 * "added four foods" is a much worse sentence than the four foods you ate on Monday.
 */
@Composable
fun ShareReceivedDialog(
    envelope: ShareEnvelopeV1,
    onAdd: () -> Unit,
    onDiscard: () -> Unit,
) {
    NomiDialog(
        onDismissRequest = onDiscard,
        title = nomiString("A day arrived"),
        icon = Icons.Default.Nfc,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = nomiFormat(
                        "Shared from another Nomi, eaten on {0}",
                        envelope.day.date,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                envelope.day.foods.forEach { ShareArrivedFood(it) }
                envelope.day.totals?.let { totals ->
                    HorizontalDivider()
                    Text(
                        text = nomiFormat(
                            "{0} across {1} foods",
                            "${totals.kcal.roundToInt()} kcal",
                            totals.foodCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmLabel = nomiString("Add"),
        onConfirm = onAdd,
        dismissLabel = nomiString("Discard"),
        onDismissAction = onDiscard,
    )
}

@Composable
private fun ShareArrivedFood(food: ShareFoodV1) {
    NomiCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = listOfNotNull(food.name, food.brand).joinToString(" · "),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nomiFormat(
                        "Protein {0} · carbs {1} · fat {2} g",
                        food.proteinGrams.oneDecimal(),
                        food.carbohydrateGrams.oneDecimal(),
                        food.fatGrams.oneDecimal(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${food.kcal.roundToInt()} kcal",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Whole grams without a trailing ".0", which is how the rest of the interface writes them. */
private fun Double.oneDecimal(): String = when {
    !isFinite() -> "0"
    this % 1.0 == 0.0 -> roundToInt().toString()
    else -> String.format(Locale.getDefault(), "%.1f", this)
}
