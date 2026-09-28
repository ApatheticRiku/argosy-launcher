package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity

internal fun PendingSyncQueueEntity.targetsRomOf(game: GameEntity?): Boolean = game?.rommId == rommId
