package com.nendo.argosy.ui.components.appfolder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.screens.apps.AppCard
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

data class AppFolderApp(val packageName: String, val label: String)

data class AppFolderOpen(val name: String, val apps: List<AppFolderApp>)

data class AppFolderDraft(
    val tileId: Long?,
    val name: String,
    val selected: List<String>,
    val apps: List<AppFolderApp>
)

fun List<String>.toggled(packageName: String): List<String> =
    if (packageName in this) this - packageName else this + packageName

private const val FOLDER_COLUMNS = 4
private const val MOSAIC_SIDE = 2

private fun gridMove(index: Int, size: Int, delta: Int): Int =
    if (size == 0) 0 else (index + delta).mod(size)

private fun gridStep(index: Int, size: Int, rows: Int): Int {
    val target = index + rows * FOLDER_COLUMNS
    return if (target in 0 until size) target else index
}

@Composable
fun AppFolderIconMosaic(packages: List<String>, size: Dp, modifier: Modifier = Modifier) {
    val cell = size / MOSAIC_SIDE
    Column(modifier = modifier.size(size)) {
        packages.take(MOSAIC_SIDE * MOSAIC_SIDE).chunked(MOSAIC_SIDE).forEach { row ->
            Row {
                row.forEach { packageName ->
                    coil.compose.AsyncImage(
                        model = AppIconData(packageName),
                        contentDescription = null,
                        modifier = Modifier.size(cell).padding(Dimens.spacingXs)
                    )
                }
            }
        }
    }
}

@Composable
fun AppFolderOverlay(
    folder: AppFolderOpen,
    onLaunch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val inputDispatcher = LocalInputDispatcher.current
    var focusIndex by remember(folder) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val currentFolder by rememberUpdatedState(folder)
    val currentOnLaunch by rememberUpdatedState(onLaunch)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(focusIndex) {
        if (folder.apps.isNotEmpty()) gridState.animateScrollToItem(focusIndex)
    }

    val handler = remember {
        object : InputHandler {
            private val size get() = currentFolder.apps.size
            override fun onLeft(): InputResult {
                focusIndex = gridMove(focusIndex, size, -1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                focusIndex = gridMove(focusIndex, size, 1)
                return InputResult.HANDLED
            }
            override fun onUp(): InputResult {
                focusIndex = gridStep(focusIndex, size, -1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                focusIndex = gridStep(focusIndex, size, 1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                val app = currentFolder.apps.getOrNull(focusIndex) ?: return InputResult.HANDLED
                currentOnLaunch(app.packageName)
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = handler, inputDispatcher = inputDispatcher)

    CenteredModal(title = folder.name, onDismiss = onDismiss) {
        if (folder.apps.isEmpty()) {
            Text(
                text = stringResource(R.string.fork_home_folder_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Dimens.spacingLg)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(FOLDER_COLUMNS),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                modifier = Modifier.fillMaxWidth().heightIn(max = FOLDER_LIST_MAX_HEIGHT)
            ) {
                itemsIndexed(folder.apps, key = { _, app -> app.packageName }) { index, app ->
                    AppCard(
                        packageName = app.packageName,
                        label = app.label,
                        isFocused = index == focusIndex,
                        onClick = { onLaunch(app.packageName) }
                    )
                }
            }
        }
    }
}

private enum class SetupStep { NAME, APPS }

@Composable
fun AppFolderSetupFlow(
    draft: AppFolderDraft,
    onSave: (name: String, packages: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultName = stringResource(R.string.fork_home_folder_default_name)
    var step by remember(draft) { mutableStateOf(SetupStep.NAME) }
    var name by remember(draft) { mutableStateOf(draft.name.ifBlank { defaultName }) }
    var selected by remember(draft) { mutableStateOf(draft.selected) }
    when (step) {
        SetupStep.NAME -> FolderNameStep(
            name = name,
            onNameChange = { name = it },
            onNext = { step = SetupStep.APPS },
            onDismiss = onDismiss
        )
        SetupStep.APPS -> FolderAppsStep(
            name = name.ifBlank { defaultName },
            apps = draft.apps,
            selected = selected,
            onToggle = { selected = selected.toggled(it) },
            onSave = { onSave(name.ifBlank { defaultName }, selected) },
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun FolderNameStep(
    name: String,
    onNameChange: (String) -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    val inputDispatcher = LocalInputDispatcher.current
    var focus by remember { mutableStateOf(TextEntryFocus()) }
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val handler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focus = focus.copy(row = TextEntryRow.FIELD)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                focus = focus.copy(row = TextEntryRow.BUTTONS)
                return InputResult.HANDLED
            }
            override fun onLeft(): InputResult {
                if (focus.row == TextEntryRow.BUTTONS) focus = focus.copy(buttonIndex = TEXT_ENTRY_CANCEL_BUTTON)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (focus.row == TextEntryRow.BUTTONS) focus = focus.copy(buttonIndex = TEXT_ENTRY_CONFIRM_BUTTON)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                when {
                    focus.row == TextEntryRow.FIELD -> focus = focus.requestKeyboard()
                    focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> currentOnDismiss()
                    else -> currentOnNext()
                }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = handler, inputDispatcher = inputDispatcher)

    TextEntryModal(
        title = stringResource(R.string.fork_home_folder_name_title),
        label = stringResource(R.string.fork_home_folder_name_title),
        confirmLabel = stringResource(R.string.fork_home_folder_name_next),
        cancelLabel = stringResource(R.string.fork_home_folder_cancel),
        text = name,
        onTextChange = onNameChange,
        onDismiss = onDismiss,
        onSubmit = onNext,
        focus = focus,
        canSubmit = true
    )
}

@Composable
private fun FolderAppsStep(
    name: String,
    apps: List<AppFolderApp>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val inputDispatcher = LocalInputDispatcher.current
    var focusIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val rowCount = apps.size + 1
    val currentApps by rememberUpdatedState(apps)
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentRowCount by rememberUpdatedState(rowCount)

    LaunchedEffect(focusIndex) { listState.animateScrollToItem(focusIndex) }

    val handler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focusIndex = (focusIndex - 1).mod(currentRowCount)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                focusIndex = (focusIndex + 1).mod(currentRowCount)
                return InputResult.HANDLED
            }
            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onConfirm(): InputResult {
                if (focusIndex == 0) {
                    currentOnSave()
                    return InputResult.handled(SoundType.SELECT)
                }
                val app = currentApps.getOrNull(focusIndex - 1) ?: return InputResult.HANDLED
                currentOnToggle(app.packageName)
                return InputResult.handled(SoundType.TOGGLE)
            }
            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = handler, inputDispatcher = inputDispatcher)

    CenteredModal(title = stringResource(R.string.fork_home_folder_pick_title, name), onDismiss = onDismiss) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = FOLDER_LIST_MAX_HEIGHT)) {
            item(key = "save") {
                FolderRow(
                    label = stringResource(R.string.fork_home_folder_save),
                    isFocused = focusIndex == 0,
                    leading = {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(Dimens.iconMd))
                    },
                    onClick = onSave
                )
            }
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val checked = app.packageName in selected
                FolderRow(
                    label = app.label,
                    isFocused = focusIndex == index + 1,
                    leading = {
                        Box {
                            coil.compose.AsyncImage(
                                model = AppIconData(app.packageName),
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.iconMd)
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.iconMd)
                        )
                    },
                    onClick = {
                        focusIndex = index + 1
                        onToggle(app.packageName)
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    label: String,
    isFocused: Boolean,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoFocus(onClick = onClick)
            .background(if (isFocused) theme.focusAccent.copy(alpha = FOCUS_FILL_ALPHA) else Color.Transparent)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        leading()
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

private const val FOCUS_FILL_ALPHA = 0.15f

private val FOLDER_LIST_MAX_HEIGHT =
    com.nendo.argosy.ui.theme.generated.DimensionTokens.Layout.slotPickerListMaxHeight.dp
