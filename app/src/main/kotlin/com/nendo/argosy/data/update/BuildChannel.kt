package com.nendo.argosy.data.update

import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.remote.github.VersionInfo

/**
 * Whether a build is a prerelease: a debug build, or a version name whose suffix is a prerelease
 * qualifier such as `3.0.0-beta.1`. A build suffix such as `3.0.0-fork.1` is stable.
 * [isPrereleaseBuild] is the answer for the running build.
 */
object BuildChannel {

    fun isPrerelease(isDebugBuild: Boolean, versionName: String): Boolean =
        isDebugBuild || (VersionInfo.parse(versionName)?.isPrerelease ?: versionName.contains(PRERELEASE_SEPARATOR))

    val isPrereleaseBuild: Boolean = isPrerelease(BuildConfig.DEBUG, BuildConfig.VERSION_NAME)

    private const val PRERELEASE_SEPARATOR = '-'
}
