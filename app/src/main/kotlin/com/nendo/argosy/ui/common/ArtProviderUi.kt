package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.model.ArtProvider

@get:StringRes
val ArtProvider.labelRes: Int
    get() = when (this) {
        ArtProvider.ROMM -> R.string.gamedetail_art_picker_origin_romm
        ArtProvider.SCREENSCRAPER -> R.string.gamedetail_art_picker_origin_screenscraper
        ArtProvider.LAUNCHBOX -> R.string.gamedetail_art_picker_origin_launchbox
    }
