package com.nendo.argosy.data.model

enum class ArtProvider { ROMM, SCREENSCRAPER, LAUNCHBOX }

data class ServerArt(val url: String, val provider: ArtProvider)
