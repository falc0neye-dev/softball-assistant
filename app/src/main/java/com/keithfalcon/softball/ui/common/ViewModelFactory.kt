package com.keithfalcon.softball.ui.common

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.keithfalcon.softball.SoftballApp

/** Manual DI: build any ViewModel from the app singleton (spec §2 — Hilt optional, app is small). */
@Composable
inline fun <reified VM : ViewModel> softballViewModel(
    key: String? = null,
    noinline builder: (SoftballApp) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as SoftballApp
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = builder(app) as T
        },
    )
}

val Application.softballApp: SoftballApp get() = this as SoftballApp
