package com.nendo.argosy.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable

val WindowInsets.Companion.launcherContent: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = systemBars.union(displayCutout)
