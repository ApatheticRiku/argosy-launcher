package com.nendo.argosy.data.sync.fixtures

import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import io.mockk.coEvery
import io.mockk.mockk

fun notRoutedSigil(): SigilSaveHandler = mockk<SigilSaveHandler>(relaxed = true).also { sigil ->
    coEvery { sigil.route(any(), any()) } returns null
    coEvery { sigil.collect(any(), any()) } returns SigilCollect.NotRouted
    coEvery { sigil.restore(any(), any(), any()) } returns SigilRestore.NotRouted
}
