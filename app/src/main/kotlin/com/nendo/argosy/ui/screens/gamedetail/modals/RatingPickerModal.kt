package com.nendo.argosy.ui.screens.gamedetail.modals

import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.ALauncherColors
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.StepperControl
import com.nendo.argosy.ui.screens.gamedetail.RatingType

@Composable
fun RatingPickerModal(
    type: RatingType,
    value: Int,
    onValueChange: (Int) -> Unit,
    onAdjust: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = when (type) {
        RatingType.OPINION -> stringResource(R.string.gamedetail_rating_picker_title_rating)
        RatingType.DIFFICULTY -> stringResource(R.string.gamedetail_rating_picker_title_difficulty)
        RatingType.PROGRESS -> stringResource(R.string.gamedetail_rating_picker_title_progress)
    }

    CenteredModal(
        title = title,
        baseWidth = 420.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_HORIZONTAL to
                stringResource(R.string.gamedetail_rating_picker_footer_adjust),
            InputButton.A to stringResource(R.string.gamedetail_rating_picker_footer_confirm),
            InputButton.B to stringResource(R.string.gamedetail_rating_picker_footer_cancel)
        ),
        onHintClick = { button ->
            when (button) {
                InputButton.A -> onConfirm()
                InputButton.B -> onDismiss()
                else -> Unit
            }
        }
    ) {
        if (type == RatingType.PROGRESS) {
            StepperControl(
                display = if (value == 0) {
                    stringResource(R.string.gamedetail_rating_picker_unset)
                } else {
                    stringResource(R.string.gamedetail_rating_picker_progress_value, value)
                },
                focused = true,
                onDecrement = { onAdjust(-1) },
                onIncrement = { onAdjust(1) },
                numericValue = value,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            ScoreIcons(isRating = type == RatingType.OPINION, value = value, onValueChange = onValueChange)
        }
    }
}

@Composable
private fun ColumnScope.ScoreIcons(isRating: Boolean, value: Int, onValueChange: (Int) -> Unit) {
    val filledIcon = if (isRating) Icons.Default.Star else Icons.Default.Whatshot
    val outlineIcon = if (isRating) Icons.Default.StarOutline else Icons.Outlined.Whatshot
    val filledColor = if (isRating) ALauncherColors.StarGold else ALauncherColors.DifficultyRed
    val outlineColor = Color.White.copy(alpha = 0.4f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        for (i in 1..10) {
            val isFilled = i <= value
            Icon(
                imageVector = if (isFilled) filledIcon else outlineIcon,
                contentDescription = null,
                tint = if (isFilled) filledColor else outlineColor,
                modifier = Modifier
                    .size(Dimens.iconLg)
                    .clickableNoFocus { onValueChange(i) }
            )
        }
    }

    Text(
        text = if (value == 0) {
            stringResource(R.string.gamedetail_rating_picker_unset)
        } else {
            stringResource(R.string.gamedetail_rating_picker_value, value)
        },
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
}
