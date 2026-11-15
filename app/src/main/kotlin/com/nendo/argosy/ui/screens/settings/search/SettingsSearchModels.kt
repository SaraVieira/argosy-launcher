package com.nendo.argosy.ui.screens.settings.search

import com.nendo.argosy.ui.common.DisplayText
import com.nendo.argosy.ui.screens.settings.SettingsSection

sealed interface SettingsSearchTarget {
    val id: String

    data class Row(val section: SettingsSection, val key: String) : SettingsSearchTarget {
        override val id: String get() = "row:${section.name}:$key"
    }

    data class Platform(val platformId: Long, val rowKey: String? = null) : SettingsSearchTarget {
        override val id: String get() = if (rowKey == null) "platform:$platformId" else "platform:$platformId:$rowKey"
    }
}

data class SettingsSearchEntry(
    val target: SettingsSearchTarget,
    val label: DisplayText,
    val keywords: DisplayText?,
    val path: List<DisplayText>
)

data class SettingsSearchResult(
    val entry: SettingsSearchEntry,
    val matchStart: Int,
    val matchLength: Int
)

data class SettingsSearchState(
    val query: String = "",
    val keyboardOpen: Boolean = false,
    val results: List<SettingsSearchResult> = emptyList()
) {
    val isActive: Boolean get() = query.isNotBlank()
}
