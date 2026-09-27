package com.nomi.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.components.NomiCard
import com.nomi.app.ui.components.NomiShapes
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.NomiTheme
import com.nomi.app.ui.theme.animationsAreDisabled
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiLayoutMotionSpec
import kotlinx.coroutines.delay

/**
 * The beats of the welcome demo: a meal is written, it is taken apart, its numbers are accounted
 * for, and then the three ways of getting a meal in are named.
 */
internal enum class WelcomeStage { TYPING, SPLITTING, SOURCING, PHOTO, BARCODE, LABEL, RESTING }

/**
 * The intro's state, as a pure value advanced one tick at a time.
 *
 * Nothing here knows about time, Compose or Android: [tick] is a total function, so the whole
 * sequence is testable by calling it in a loop and asserting where it lands. That is the same
 * bargain the rest of the app makes with `FoodEditRouter` and `PortionEditParser`, and it is why
 * the reveal can be tuned without a device in the loop.
 *
 * The whole run is [TotalTicks] ticks, about [TotalTicks] * [TickMillis] milliseconds. It is
 * deliberately short: a user who already knows what a nutrition app does should not be held
 * hostage to an animation, which is also why [skip] exists and the card is tappable.
 */
internal data class WelcomeStoryboardState(
    val totalCharacters: Int,
    val typedCharacters: Int = 0,
    val stage: WelcomeStage = WelcomeStage.TYPING,
    val stageTicks: Int = 0,
) {
    /** Rows appear on their own ticks so the split reads as two beats, not one jump. */
    fun isRowVisible(index: Int): Boolean = when (stage) {
        WelcomeStage.TYPING -> false
        WelcomeStage.SPLITTING -> stageTicks >= index * RowStaggerTicks
        else -> true
    }

    fun isSourcingVisible(): Boolean = stage != WelcomeStage.TYPING && stage != WelcomeStage.SPLITTING

    /**
     * The method row slides in with the first method beat and then stays.
     *
     * It would be tidier to hide it again at rest, and that was the first version - but the
     * resting state is the last thing on screen before the questions start, and the one worth
     * remembering. It should show all three ways in, with none of them highlighted.
     */
    fun isMethodRowVisible(): Boolean = stage == WelcomeStage.PHOTO ||
        stage == WelcomeStage.BARCODE ||
        stage == WelcomeStage.LABEL ||
        stage == WelcomeStage.RESTING

    /**
     * Which input method is being named, or null outside those beats.
     *
     * The rows and the total stay exactly where they are through these: the point is that four
     * different ways of describing a meal arrive at the same precise answer, so moving the numbers
     * around would work against that.
     */
    val activeMethodIndex: Int?
        get() = when (stage) {
            WelcomeStage.PHOTO -> 0
            WelcomeStage.BARCODE -> 1
            WelcomeStage.LABEL -> 2
            else -> null
        }

    val isResting: Boolean get() = stage == WelcomeStage.RESTING

    val typedFraction: Float
        get() = if (totalCharacters <= 0) 1f else (typedCharacters.toFloat() / totalCharacters).coerceIn(0f, 1f)

    fun tick(): WelcomeStoryboardState = when (stage) {
        WelcomeStage.TYPING -> if (typedCharacters + CharactersPerTick >= totalCharacters) {
            copy(
                typedCharacters = totalCharacters,
                stage = WelcomeStage.SPLITTING,
                stageTicks = 0,
            )
        } else {
            copy(typedCharacters = typedCharacters + CharactersPerTick)
        }

        WelcomeStage.SPLITTING -> advance(WelcomeStage.SOURCING, SplittingTicks)
        WelcomeStage.SOURCING -> advance(WelcomeStage.PHOTO, SourcingTicks)
        WelcomeStage.PHOTO -> advance(WelcomeStage.BARCODE, MethodTicks)
        WelcomeStage.BARCODE -> advance(WelcomeStage.LABEL, MethodTicks)
        WelcomeStage.LABEL -> advance(WelcomeStage.RESTING, MethodTicks)
        WelcomeStage.RESTING -> this
    }

    /** A tap anywhere on the card lands the sequence on its finished state, immediately. */
    fun skip(): WelcomeStoryboardState = WelcomeStoryboardState(
        totalCharacters = totalCharacters,
        typedCharacters = totalCharacters,
        stage = WelcomeStage.RESTING,
    )

    private fun advance(next: WelcomeStage, ticks: Int): WelcomeStoryboardState =
        if (stageTicks + 1 >= ticks) {
            copy(stage = next, stageTicks = 0)
        } else {
            copy(stageTicks = stageTicks + 1)
        }

    companion object {
        const val CharactersPerTick = 3
        const val RowStaggerTicks = 3
        const val SplittingTicks = 8
        const val SourcingTicks = 6
        const val MethodTicks = 14
        const val TickMillis = 50L
        const val MethodCount = 3

        /**
         * The English demo sentence, which is what [TotalTicks] is budgeted against. Translations
         * run longer or shorter; the loop is driven by [tick] until it rests, not by this count.
         */
        const val ReferenceSentenceLength = 46

        /** Roughly 3.6 s end to end. */
        val TotalTicks: Int
            get() = ReferenceSentenceLength / CharactersPerTick + 1 +
                SplittingTicks + SourcingTicks + MethodTicks * MethodCount
    }
}

/** One food in the demo, already resolved into a name and a number. */
internal data class WelcomeDemoItem(
    val name: String,
    val grams: Int,
    val kilocalories: Int,
)

/**
 * The three ways of getting a meal in besides typing.
 *
 * These are named in order because that is the order of surprise: a photo first, because it is the
 * one people expect a camera app to do, then the two scanners.
 */
internal enum class WelcomeMethod(val labelKey: String, val icon: ImageVector) {
    PHOTO("Photo", Icons.Outlined.CameraAlt),
    BARCODE("Barcode", Icons.Outlined.QrCodeScanner),
    LABEL("Nutrition label", Icons.Outlined.Description),
}

/**
 * The demo meal, with the quantities spelled out.
 *
 * Every amount is a real weight in grams. The point of the beat is that Nomi's output is precise
 * where the input was loose, so a demo that said "a handful" anywhere — in the sentence, in a row,
 * or in the running total — would be arguing against the app's own promise. The figures are typical
 * values for the foods as described, and the card is labelled as an example rather than presented
 * as a logged entry, so nothing here can be mistaken for a measurement.
 */
internal data class WelcomeDemo(
    val sentence: String,
    val items: List<WelcomeDemoItem>,
) {
    val totalKilocalories: Int get() = items.sumOf { it.kilocalories }
}

@Composable
private fun rememberWelcomeDemo(): WelcomeDemo = WelcomeDemo(
    sentence = nomiString("80 g of blueberries with 200 g of Greek yoghurt"),
    items = listOf(
        WelcomeDemoItem(
            name = nomiString("Blueberries"),
            grams = 80,
            kilocalories = 46,
        ),
        WelcomeDemoItem(
            name = nomiString("Greek yoghurt, 2 % fat"),
            grams = 200,
            kilocalories = 146,
        ),
    ),
)

/**
 * The animated demo on the welcome screen.
 *
 * Shows one sentence being written, resolving into per-food rows with real weights, and ending on a
 * total with its provenance. Nothing loops and nothing waits for input: it plays once, in about two
 * seconds, and settles. Tapping the card skips to the end, and if the system animation scale is
 * zero it never plays at all.
 */
@Composable
internal fun NomiWelcomeStoryboard(
    modifier: Modifier = Modifier,
    demo: WelcomeDemo = rememberWelcomeDemo(),
) {
    val animationsDisabled = animationsAreDisabled()
    var state by remember(demo.sentence) {
        mutableStateOf(WelcomeStoryboardState(totalCharacters = demo.sentence.length))
    }

    LaunchedEffect(demo.sentence, animationsDisabled) {
        if (animationsDisabled) {
            state = state.skip()
            return@LaunchedEffect
        }
        while (!state.isResting) {
            delay(WelcomeStoryboardState.TickMillis)
            state = state.tick()
        }
    }

    // Resolved out here rather than inside the semantics block: that block is not a composable
    // scope, and the card needs one description for the whole sequence rather than one per beat.
    val demoDescription = nomiString(
        "Example: a meal written in plain language, split into foods, with its calories and sources",
    )

    NomiCard(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(demo.sentence) {
                detectTapGestures { state = state.skip() }
            }
            .semantics { contentDescription = demoDescription },
        contentPadding = PaddingValues(20.dp),
        spacing = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (state.stage) {
                    WelcomeStage.TYPING -> nomiString("Write a meal the way you'd say it.")
                    WelcomeStage.SPLITTING -> nomiString("Nomi reads that as separate foods.")
                    WelcomeStage.SOURCING, WelcomeStage.RESTING ->
                        nomiString("Every number shows where it came from.")
                    WelcomeStage.PHOTO -> nomiString("Or take a photo of your plate.")
                    WelcomeStage.BARCODE -> nomiString("Or scan a barcode.")
                    WelcomeStage.LABEL -> nomiString("Or read a nutrition label.")
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            ExampleChip()
        }

        // Beat one: the sentence, written out.
        AnimatedVisibility(
            visible = state.stage == WelcomeStage.TYPING,
            enter = fadeIn(animationSpec = nomiFadeMotionSpec()) +
                expandVertically(animationSpec = nomiLayoutMotionSpec()),
            exit = fadeOut(animationSpec = nomiFadeMotionSpec()) +
                shrinkVertically(animationSpec = nomiLayoutMotionSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = demo.sentence.take(state.typedCharacters) + TypingCaret,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Beat two: the same meal, as rows with weights on them.
        AnimatedVisibility(
            visible = state.stage != WelcomeStage.TYPING,
            enter = fadeIn(animationSpec = nomiFadeMotionSpec()),
            exit = fadeOut(animationSpec = nomiFadeMotionSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                demo.items.forEachIndexed { index, item ->
                    AnimatedVisibility(
                        visible = state.isRowVisible(index),
                        enter = fadeIn(animationSpec = nomiFadeMotionSpec()) +
                            expandVertically(animationSpec = nomiLayoutMotionSpec()),
                    ) {
                        WelcomeDemoRow(item = item)
                    }
                }
            }
        }

        // Beat three: the total, and where its numbers came from.
        AnimatedVisibility(
            visible = state.isSourcingVisible(),
            enter = fadeIn(animationSpec = nomiFadeMotionSpec()) +
                expandVertically(animationSpec = nomiLayoutMotionSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = nomiString("Total"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = nomiFormat("{0} kcal", demo.totalKilocalories),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = nomiString("2 cited sources · high confidence"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Beat four: the other three ways in. The rows and the total stay put while the caption
        // and one tile move, because the claim is that the same precise answer comes out of all
        // four - not that four different answers exist.
        WelcomeMethodRow(
            visible = state.isMethodRowVisible(),
            activeIndex = state.activeMethodIndex,
        )
    }
}

/**
 * Photo, barcode, label, as three tiles that light up in turn.
 *
 * One row rather than three more full card states: the demonstration has to stay inside a couple
 * of seconds, and a row of tiles costs one line of height instead of three screen transitions.
 */
@Composable
private fun WelcomeMethodRow(visible: Boolean, activeIndex: Int?) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = nomiFadeMotionSpec()) +
            expandVertically(animationSpec = nomiLayoutMotionSpec()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WelcomeMethod.entries.forEachIndexed { index, method ->
                WelcomeMethodTile(
                    method = method,
                    active = index == activeIndex,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WelcomeMethodTile(
    method: WelcomeMethod,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    // The highlight eases rather than snaps, so moving between two tiles reads as one movement.
    val container by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = nomiLayoutMotionSpec(),
        label = "welcome_method_${method.name}",
    )
    val content by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = nomiLayoutMotionSpec(),
        label = "welcome_method_content_${method.name}",
    )

    Surface(
        modifier = modifier,
        shape = NomiShapes.MenuItem,
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = method.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = nomiString(method.labelKey),
                style = MaterialTheme.typography.labelSmall,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val TypingCaret = "▍"

@Composable
private fun WelcomeDemoRow(item: WelcomeDemoItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = nomiFormat("{0} g", item.grams),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = nomiFormat("{0} kcal", item.kilocalories),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * The demo is labelled, so it cannot be read as something the user logged.
 *
 * Nomi's whole argument is that a number without provenance is a guess. An onboarding animation
 * showing invented calories without saying they are invented would undercut that at the exact
 * moment the user is deciding whether to trust the app.
 */
@Composable
private fun ExampleChip() {
    Surface(
        shape = NomiShapes.MenuItem,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = nomiString("Example"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 420)
@Composable
private fun WelcomeStoryboardRestingPreview() {
    NomiTheme(dynamicColor = false) {
        Box(Modifier.padding(16.dp)) {
            NomiWelcomeStoryboard(
                demo = WelcomeDemo(
                    sentence = "80 g of blueberries with 200 g of Greek yoghurt",
                    items = listOf(
                        WelcomeDemoItem("Blueberries", 80, 46),
                        WelcomeDemoItem("Greek yoghurt, 2 % fat", 200, 146),
                    ),
                ),
            )
        }
    }
}
