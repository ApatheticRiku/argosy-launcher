package com.nendo.argosy.data.sync.fixtures

import com.nendo.argosy.data.sync.snapshot.SnapshotSyncRouter
import io.mockk.coEvery
import io.mockk.mockk

fun legacySnapshotRouter(): dagger.Lazy<SnapshotSyncRouter> {
    val router = mockk<SnapshotSyncRouter>()
    coEvery { router.preLaunch(any(), any(), any()) } returns null
    coEvery { router.upload(any(), any(), any(), any(), any()) } returns null
    coEvery { router.download(any(), any(), any()) } returns null
    return dagger.Lazy { router }
}
