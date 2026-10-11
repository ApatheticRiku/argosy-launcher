package com.nendo.argosy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

object LauncherViewModelStore : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
inline fun <reified VM : ViewModel> launcherViewModel(): VM {
    val host = checkNotNull(LocalViewModelStoreOwner.current) as HasDefaultViewModelProviderFactory
    return remember {
        val extras = MutableCreationExtras(host.defaultViewModelCreationExtras).apply {
            set(VIEW_MODEL_STORE_OWNER_KEY, LauncherViewModelStore)
        }
        ViewModelProvider(LauncherViewModelStore.viewModelStore, host.defaultViewModelProviderFactory, extras)
            .get(VM::class.java)
    }
}
