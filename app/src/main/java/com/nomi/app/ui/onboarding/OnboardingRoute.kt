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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.nomi.app.domain.model.NutritionPlan
import com.nomi.app.domain.model.OnboardingDraft

@Composable
fun OnboardingRoute(
    onComplete: (OnboardingDraft, NutritionPlan) -> Unit,
    modifier: Modifier = Modifier,
    onDraftChanged: (OnboardingDraft) -> Unit = {},
    viewModel: OnboardingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.draft) {
        onDraftChanged(state.draft)
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
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

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

@Composable
private fun OnboardingTopBar(
    state: OnboardingUiState,
    onBack: () -> Unit,
) {
    val journey = state.activeJourney()
    val stepNumber = journey.indexOf(state.currentStep).coerceAtLeast(1)

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
                    contentDescription = "Back",
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Step $stepNumber of ${journey.lastIndex}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .testTag("onboarding_progress"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun onboardingTransition(
    forward: Boolean,
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
): ContentTransform {
    val direction = if (forward) 1 else -1
    return (
        slideInHorizontally(animationSpec = spatialSpec) { width -> direction * width / 4 } +
            fadeIn(animationSpec = effectsSpec)
        ).togetherWith(
        slideOutHorizontally(animationSpec = spatialSpec) { width -> -direction * width / 5 } +
            fadeOut(animationSpec = effectsSpec),
    )
}
