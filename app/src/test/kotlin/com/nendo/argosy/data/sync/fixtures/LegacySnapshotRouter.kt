package com.nendo.argosy.data.sync.fixtures

import com.nendo.argosy.data.sync.snapshot.SnapshotSyncRouter
import io.mockk.coEvery
import io.mockk.mockk

fun legacySnapshotRouter(): dagger.Lazy<SnapshotSyncRouter> {
    val router = mockk<SnapshotSyncRouter>()
    coEvery { router.handles(any()) } returns false
    coEvery { router.sessionChannel(any(), any(), any()) } answers { if (secondArg<Boolean>()) null else thirdArg() }
    coEvery { router.launchChannel(any(), any()) } answers { secondArg() }
    coEvery { router.preLaunch(any(), any(), any()) } returns null
    coEvery { router.upload(any(), any(), any(), any(), any()) } returns null
    coEvery { router.download(any(), any(), any()) } returns null
    coEvery { router.uploadCached(any(), any(), any(), any(), any(), any(), any()) } returns null
    return dagger.Lazy { router }
}
