package com.nendo.argosy.fork

import android.content.pm.PackageManager
import com.nendo.argosy.ui.dualscreen.dashboard.DashboardAppShortcut

object InGameAppPins {
    const val GAME_TWIN_PACKAGE = "com.twinlore.app"

    val DEFAULT: List<String> = listOf(GAME_TWIN_PACKAGE)

    fun shortcuts(packageManager: PackageManager, packages: List<String>): List<DashboardAppShortcut> =
        packages.mapNotNull { packageName ->
            label(packageManager, packageName)?.let { DashboardAppShortcut(packageName, it) }
        }

    fun label(packageManager: PackageManager, packageName: String): String? = runCatching {
        packageManager.getLaunchIntentForPackage(packageName) ?: return null
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
