package com.nendo.argosy.ui.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import java.util.Locale

@Composable
fun RuledSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    color: Color = LocalArgosyTheme.current.textPrimary,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = title,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.borderThin)
                .background(theme.hairlineHigh)
        )
        trailing()
    }
}

@Composable
fun PanelSectionHeader(title: String, modifier: Modifier = Modifier) {
    RuledSectionHeader(
        title = title.uppercase(Locale.getDefault()),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = ComponentDefaults.QuickPanel.sectionLabelTrackingSp.sp
        ),
        color = LocalArgosyTheme.current.textDim
    )
}
