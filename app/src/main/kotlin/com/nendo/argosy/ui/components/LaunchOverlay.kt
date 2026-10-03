package com.nendo.argosy.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.ui.common.statusMessage
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LaunchProgressEntryPoint {
    fun launchProgressTracker(): LaunchProgressTracker
}

private fun launchProgressTracker(context: Context): LaunchProgressTracker =
    EntryPointAccessors.fromApplication(context.applicationContext, LaunchProgressEntryPoint::class.java)
        .launchProgressTracker()

/**
 * The launch in progress over the surface that starts launches. It holds the input stack while a
 * launch runs and ends the launch once that surface stops.
 */
@Composable
fun LaunchOverlay(
    modifier: Modifier = Modifier,
    viewModel: LaunchOverlayViewModel = hiltViewModel()
) {
    val progress by viewModel.progress.collectAsState()
    val promptFocus by viewModel.promptFocus.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.hostStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    ModalInputEffect(active = progress != null, handler = viewModel.inputHandler)

    LaunchOverlayContent(
        progress = progress,
        promptFocus = promptFocus,
        onAnswer = viewModel::answer,
        onCancel = { viewModel.cancel() },
        modifier = modifier
    )
}

@Composable
fun LaunchOverlayMirror(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tracker = remember { launchProgressTracker(context) }
    val progress by tracker.progress.collectAsState()
    LaunchOverlayContent(
        progress = progress,
        promptFocus = NO_PROMPT_FOCUS,
        onAnswer = tracker::answer,
        onCancel = { tracker.cancel() },
        modifier = modifier
    )
}

private const val NO_PROMPT_FOCUS = -1

@Composable
private fun LaunchOverlayContent(
    progress: LaunchProgress?,
    promptFocus: Int,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastShown by remember { mutableStateOf<LaunchProgress?>(null) }
    SideEffect { progress?.let { lastShown = it } }

    val scrimAlpha = if (LocalLauncherTheme.current.isDarkTheme) {
        ComponentDefaults.LaunchOverlay.scrimAlphaDark
    } else {
        ComponentDefaults.LaunchOverlay.scrimAlphaLight
    }
    val scrimColor = MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha)

    AnimatedVisibility(
        visible = progress != null,
        enter = fadeIn(tween(ComponentDefaults.LaunchOverlay.fadeMs)),
        exit = fadeOut(tween(ComponentDefaults.LaunchOverlay.fadeMs)),
        modifier = modifier.fillMaxSize().focusProperties { canFocus = false }
    ) {
        val shown = progress ?: lastShown ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickableNoFocus { },
            contentAlignment = Alignment.Center
        ) {
            when (val step = shown.step) {
                is LaunchStep.Prompt -> PromptContent(
                    gameTitle = shown.gameTitle,
                    prompt = step,
                    focusIndex = promptFocus,
                    onAnswer = onAnswer,
                    onCancel = onCancel
                )
                else -> StepContent(
                    gameTitle = shown.gameTitle,
                    step = step,
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
private fun StepContent(
    gameTitle: String?,
    step: LaunchStep,
    onCancel: () -> Unit
) {
    val rotation by rememberInfiniteTransition(label = "launch_spin").animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            tween(ComponentDefaults.LaunchOverlay.spinMs, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "launch_rotation"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(ComponentDefaults.LaunchOverlay.iconSizeDp.dp)
                .rotate(rotation)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        AnimatedContent(
            targetState = step.message(),
            transitionSpec = {
                fadeIn(tween(ComponentDefaults.LaunchOverlay.stepFadeInMs)) togetherWith
                    fadeOut(tween(ComponentDefaults.LaunchOverlay.stepFadeOutMs))
            },
            label = "launchStep"
        ) { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (gameTitle != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (step is LaunchStep.DownloadingCore) {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            ArgosyProgressBar(
                progress = step.fraction,
                modifier = Modifier.width(ComponentDefaults.LaunchOverlay.progressWidthDp.dp)
            )
        }
        if (step != LaunchStep.Launching) {
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            CancelPill(onClick = onCancel)
        }
    }
}

@Composable
private fun CancelPill(onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(theme.surfaceElevated)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        InputGlyph(button = InputButton.B, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.ui_launch_overlay_cancel),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PromptContent(
    gameTitle: String?,
    prompt: LaunchStep.Prompt,
    focusIndex: Int,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit
) {
    when (val conflict = prompt.conflict) {
        is SyncProgress.HardcoreConflict -> HardcoreConflictContent(
            gameName = conflict.gameName,
            focusIndex = focusIndex,
            onKeepHardcore = { onAnswer(LaunchPromptOption.KEEP_HARDCORE) },
            onDowngradeToCasual = { onAnswer(LaunchPromptOption.DOWNGRADE_TO_CASUAL) },
            onKeepLocal = { onAnswer(LaunchPromptOption.SKIP_HARDCORE_SAVE) },
            onCancelLaunch = onCancel
        )
        is SyncProgress.LocalModified -> LocalModifiedContent(
            gameTitle = gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
            focusIndex = focusIndex,
            onKeepLocal = { onAnswer(LaunchPromptOption.APPLY_LOCAL) },
            onRestoreSelected = { onAnswer(LaunchPromptOption.RESTORE_SERVER) },
            onCancelLaunch = onCancel
        )
        else -> Unit
    }
}

@Composable
private fun LaunchStep.message(): String = when (this) {
    LaunchStep.FinishingPrevious -> stringResource(R.string.ui_launch_overlay_step_finishing_previous)
    is LaunchStep.Save -> progress.statusMessage()
    is LaunchStep.DownloadingCore -> stringResource(R.string.ui_launch_overlay_step_downloading_core)
    LaunchStep.PreparingSystemFiles -> stringResource(R.string.ui_launch_overlay_step_preparing_system_files)
    LaunchStep.PreparingUi -> stringResource(R.string.ui_launch_overlay_step_preparing_ui)
    LaunchStep.Launching -> stringResource(R.string.ui_launch_overlay_step_launching)
    is LaunchStep.Prompt -> ""
}
