package com.nendo.argosy.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.DeviceKind

val DeviceKind.icon: Painter
    @Composable get() = when (this) {
        DeviceKind.HANDHELD_HORIZONTAL -> painterResource(R.drawable.ic_device_handheld_horizontal)
        DeviceKind.HANDHELD_VERTICAL -> painterResource(R.drawable.ic_device_handheld_vertical)
        DeviceKind.DUAL_SCREEN -> painterResource(R.drawable.ic_device_dual_screen)
        DeviceKind.PHONE -> rememberVectorPainter(Icons.Default.Smartphone)
        DeviceKind.TABLET -> rememberVectorPainter(Icons.Default.TabletAndroid)
        DeviceKind.TV -> rememberVectorPainter(Icons.Default.Tv)
        DeviceKind.DESKTOP -> rememberVectorPainter(Icons.Default.Computer)
        DeviceKind.WEB -> rememberVectorPainter(Icons.Default.Language)
        DeviceKind.UNKNOWN -> rememberVectorPainter(Icons.Default.Devices)
    }
