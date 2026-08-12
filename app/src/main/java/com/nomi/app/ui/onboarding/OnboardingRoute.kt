package com.nomi.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomi.app.data.preferences.MicronutrientPreferences
import com.nomi.app.domain.model.NutritionPlan
import com.nomi.app.domain.model.OnboardingDraft
import com.nomi.app.ui.localization.nomiFormat
import com.nomi.app.ui.localization.nomiString
import com.nomi.app.ui.theme.nomiFadeMotionSpec
import com.nomi.app.ui.theme.nomiPageMotionSpec
import com.nomi.app.ui.theme.nomiProgressMotionSpec

@Composable
fun OnboardingRoute(
    onComplete: (OnboardingDraft, NutritionPlan) -> Unit,
    modifier: Modifier = Modifier,
    onDraftChanged: (OnboardingDraft) -> Unit = {},
    onMicronutrientsChanged: (MicronutrientPreferences) -> Unit = {},
    viewModel: OnboardingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.draft) {
        onDraftChanged(state.draft)
    }

    // Persisted as they are toggled rather than at the end, so a journey abandoned after this
    // step still leaves the choice made here in effect.
    LaunchedEffect(state.micronutrients) {
        onMicronutrientsChanged(state.micronutrients)
    }

    BackHandler(enabled = state.currentStep != OnboardingStep.WELCOME) {
        viewModel.goBack()
    }

    OnboardingFlow(
        state = state,
        actions = viewModel,
        onComplete = {
            state.finalPlan?.let { plan -> onComplete(state.draft, plan) }
        },
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingFlow(
    state: OnboardingUiState,
    actions: OnboardingActions,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spatialSpec = nomiPageMotionSpec<IntOffset>()
    val effectsSpec = nomiFadeMotionSpec<Float>()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            if (state.currentStep != OnboardingStep.WELCOME) {
                OnboardingTopBar(state = state, onBack = actions::goBack)
            }

            AnimatedContent(
                targetState = state.currentStep,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("onboarding_${state.currentStep.name.lowercase()}"),
                transitionSpec = {
                    onboardingTransition(
                        forward = state.navigationDirection == NavigationDirection.FORWARD,
                        spatialSpec = spatialSpec,
                        effectsSpec = effectsSpec,
                    )
                },
                label = "onboarding_step",
            ) { step ->
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeScreen(onContinue = actions::goNext)
                    OnboardingStep.DATE_OF_BIRTH -> DateOfBirthScreen(
                        dateOfBirth = state.draft.dateOfBirth,
                        error = state.validationMessage,
                        onDateSelected = actions::setDateOfBirth,
                        onContinue = actions::goNext,
                    )
                    OnboardingStep.ENERGY_SEX -> EnergySexScreen(state = state, actions = actions)
                    OnboardingStep.HEIGHT -> HeightScreen(state = state, actions = actions)
                    OnboardingStep.WEIGHT -> WeightScreen(state = state, actions = actions)
                    OnboardingStep.GOAL -> GoalScreen(state = state, actions = actions)
                    OnboardingStep.TARGET_WEIGHT -> TargetWeightScreen(state = state, actions = actions)
                    OnboardingStep.ACTIVITY -> ActivityScreen(state = state, actions = actions)
                    OnboardingStep.PROGRESS_RATE -> ProgressRateScreen(state = state, actions = actions)
                    OnboardingStep.MICRONUTRIENTS -> MicronutrientsScreen(state = state, actions = actions)
                    OnboardingStep.PLAN -> PlanRevealScreen(
                        state = state,
                        actions = actions,
                        onComplete = onComplete,
                    )
                }
            }
        }
    }
}

/**
 * How far in you are, and the way back out.
 *
 * The bar carries the wavy determinate indicator rather than the flat one. It is the one place in
 * onboarding where something is genuinely in motion between screens, and the wave animating toward
 * the stop indicator makes the progress feel like it is being made rather than merely reported.
 * The step count also animates, so the number and the bar move together. The hairline rule
 * underneath is gone: the indicator already separates the bar from the question.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OnboardingTopBar(
    state: OnboardingUiState,
    onBack: () -> Unit,
) {
    val journey = state.activeJourney()
    val stepNumber = journey.indexOf(state.currentStep).coerceAtLeast(1)
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = nomiProgressMotionSpec(),
        label = "onboarding_progress_value",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("onboarding_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = nomiString("Back"),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = nomiFormat("Step {0} of {1}", stepNumber, journey.lastIndex),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
        LinearWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
                .testTag("onboarding_progress"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

private fun onboardingTransition(
    forward: Boolean,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(animationSpec = spatialSpec) { width -> direction * width / 10 } +
            fadeIn(animationSpec = effectsSpec)
        ).togetherWith(
        slideOutHorizontally(animationSpec = spatialSpec) { width -> -direction * width / 12 } +
            fadeOut(animationSpec = effectsSpec),
    )
}
