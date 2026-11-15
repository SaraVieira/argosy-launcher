package com.nendo.argosy.fork

import android.content.pm.PackageManager

object GameTwinShortcut {
    const val PACKAGE = "com.twinlore.app"

    fun label(packageManager: PackageManager): String? = runCatching {
        packageManager.getLaunchIntentForPackage(PACKAGE) ?: return null
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(PACKAGE, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
