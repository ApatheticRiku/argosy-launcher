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
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.ui.common.statusMessage
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
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
 * The launch in progress over the whole surface. The surface that started launches passes
 * [hostsLaunch], which gives the overlay the input stack and ends a launch once the surface stops.
 */
@Composable
fun LaunchOverlay(
    modifier: Modifier = Modifier,
    hostsLaunch: Boolean = true
) {
    val context = LocalContext.current
    val tracker = remember { launchProgressTracker(context) }
    val progress by tracker.progress.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hostsLaunch) {
        if (!hostsLaunch) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) tracker.hostStopped()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var lastShown by remember { mutableStateOf<LaunchProgress?>(null) }
    SideEffect { progress?.let { lastShown = it } }

    val prompt = progress?.step as? LaunchStep.Prompt
    var promptFocus by remember(prompt) { mutableIntStateOf(0) }
    val currentProgress by rememberUpdatedState(progress)
    val inputHandler = remember(tracker) {
        LaunchOverlayInputHandler(
            current = { currentProgress },
            getPromptFocus = { promptFocus },
            setPromptFocus = { promptFocus = it },
            tracker = tracker
        )
    }
    ModalInputEffect(active = hostsLaunch && progress != null, handler = inputHandler)

    val scrimAlpha = if (LocalLauncherTheme.current.isDarkTheme) {
        ComponentDefaults.LaunchOverlay.scrimAlphaDark
    } else {
        ComponentDefaults.LaunchOverlay.scrimAlphaLight
    }
    val scrimColor = if (LocalLauncherTheme.current.isDarkTheme) Color.Black else Color.White

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
                .background(scrimColor.copy(alpha = scrimAlpha))
                .clickableNoFocus { },
            contentAlignment = Alignment.Center
        ) {
            when (val step = shown.step) {
                is LaunchStep.Prompt -> PromptContent(
                    gameTitle = shown.gameTitle,
                    prompt = step,
                    focusIndex = promptFocus,
                    onAnswer = tracker::answer,
                    onCancel = { tracker.cancel() }
                )
                else -> StepContent(
                    gameTitle = shown.gameTitle,
                    step = step,
                    onCancel = { tracker.cancel() }
                )
            }
        }
    }
}

private class LaunchOverlayInputHandler(
    private val current: () -> LaunchProgress?,
    private val getPromptFocus: () -> Int,
    private val setPromptFocus: (Int) -> Unit,
    private val tracker: LaunchProgressTracker
) : InputHandler {

    private val prompt: LaunchStep.Prompt? get() = current()?.step as? LaunchStep.Prompt

    override fun onUp(): InputResult {
        val step = prompt ?: return InputResult.HANDLED
        setPromptFocus((getPromptFocus() - 1).coerceIn(0, step.options.size))
        return InputResult.handled(SoundType.NAVIGATE)
    }

    override fun onDown(): InputResult {
        val step = prompt ?: return InputResult.HANDLED
        setPromptFocus((getPromptFocus() + 1).coerceIn(0, step.options.size))
        return InputResult.handled(SoundType.NAVIGATE)
    }

    override fun onConfirm(): InputResult {
        val step = prompt ?: return InputResult.HANDLED
        val option = step.options.getOrNull(getPromptFocus())
        if (option != null) tracker.answer(option) else tracker.cancel()
        return InputResult.handled(SoundType.SELECT)
    }

    override fun onBack(): InputResult =
        if (tracker.cancel()) InputResult.handled(SoundType.CLOSE_MODAL) else InputResult.HANDLED

    override fun onLeft(): InputResult = InputResult.HANDLED
    override fun onRight(): InputResult = InputResult.HANDLED
    override fun onMenu(): InputResult = InputResult.HANDLED
    override fun onSecondaryAction(): InputResult = InputResult.HANDLED
    override fun onContextMenu(): InputResult = InputResult.HANDLED
    override fun onPrevSection(): InputResult = InputResult.HANDLED
    override fun onNextSection(): InputResult = InputResult.HANDLED
    override fun onPrevTrigger(): InputResult = InputResult.HANDLED
    override fun onNextTrigger(): InputResult = InputResult.HANDLED
    override fun onSelect(): InputResult = InputResult.HANDLED
    override fun onLeftStickClick(): InputResult = InputResult.HANDLED
    override fun onRightStickClick(): InputResult = InputResult.HANDLED
    override fun onLongConfirm(): InputResult = InputResult.HANDLED
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
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
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
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
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
