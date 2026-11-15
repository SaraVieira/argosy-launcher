package com.nendo.argosy.ui.screens.settings.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.libretro.HotkeyManager
import com.nendo.argosy.hardware.SystemHotkeyCombos
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SAVED_DWELL_MS = 500L

/**
 * Records a Home or Back combo in press order. A lone tap of the Back button, or the system Back
 * key, cancels; the Back button held with other buttons is recorded as part of the combo.
 */
@Composable
fun SystemComboCaptureModal(
    actionTitle: String,
    onAssign: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val gamepadInputHandler = LocalGamepadInputHandler.current
    val scope = rememberCoroutineScope()
    val currentOnAssign = rememberUpdatedState(onAssign)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    var heldKeys by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var chordPeak by remember { mutableIntStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }

    DisposableEffect(gamepadInputHandler) {
        val listener: (KeyEvent) -> Boolean = { event ->
            val key = event.keyCode
            if (!isComplete && event.repeatCount == 0) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> when {
                        key == KeyEvent.KEYCODE_BACK -> currentOnDismiss.value()
                        SystemHotkeyCombos.isRecordable(key) && heldKeys.size < SystemHotkeyCombos.MAX_KEYS -> {
                            heldKeys = heldKeys + key
                            chordPeak = maxOf(chordPeak, heldKeys.size)
                        }
                    }
                    KeyEvent.ACTION_UP -> if (key in heldKeys) {
                        heldKeys = heldKeys - key
                        val loneBack = chordPeak == 1 &&
                            gamepadInputHandler?.mapKeyToEvent(key, event.deviceId) == GamepadEvent.Back
                        if (heldKeys.isEmpty()) chordPeak = 0
                        if (loneBack) currentOnDismiss.value()
                    }
                }
            }
            true
        }

        gamepadInputHandler?.setRawKeyEventListener(listener)

        onDispose {
            gamepadInputHandler?.setRawKeyEventListener(null)
        }
    }

    RecordingContent(
        title = actionTitle,
        heldKeys = heldKeys.takeIf { it.size >= SystemHotkeyCombos.MIN_KEYS } ?: emptySet(),
        isComplete = isComplete,
        onComplete = { keys ->
            scope.launch {
                delay(SAVED_DWELL_MS)
                currentOnAssign.value(keys.toList())
            }
        },
        onCancel = onDismiss,
        onUpdateComplete = { isComplete = it },
        prompt = stringResource(R.string.settings_navigation_system_combo_capture_prompt)
    )
}

@Composable
private fun RecordingContent(
    title: String,
    heldKeys: Set<Int>,
    isComplete: Boolean,
    onComplete: (Set<Int>) -> Unit,
    onCancel: () -> Unit,
    onUpdateComplete: (Boolean) -> Unit,
    prompt: String
) {
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var savedKeys by remember { mutableStateOf<Set<Int>>(emptySet()) }

    val animatedProgress by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(durationMillis = 100),
        label = "progress"
    )

    LaunchedEffect(heldKeys) {
        if (heldKeys.isNotEmpty() && !isComplete) {
            holdProgress = 0f
            savedKeys = heldKeys
            val holdDurationMs = 2000L
            val updateIntervalMs = 50L
            val steps = holdDurationMs / updateIntervalMs

            for (i in 1..steps) {
                delay(updateIntervalMs)
                if (heldKeys != savedKeys || heldKeys.isEmpty()) {
                    holdProgress = 0f
                    return@LaunchedEffect
                }
                holdProgress = i.toFloat() / steps.toFloat()
            }

            if (heldKeys == savedKeys && heldKeys.isNotEmpty()) {
                onUpdateComplete(true)
                onComplete(savedKeys)
            }
        } else if (heldKeys.isEmpty()) {
            holdProgress = 0f
            savedKeys = emptySet()
        }
    }

    Modal(
        title = stringResource(R.string.settings_hotkeys_recording_title),
        subtitle = title,
        baseWidth = 350.dp,
        onDismiss = onCancel,
        footerHints = listOf(InputButton.B to stringResource(R.string.settings_hotkeys_hint_cancel)),
        onFooterHintClick = { button -> if (button == InputButton.B) onCancel() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingXl),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(80.dp),
                        color = if (isComplete) {
                            MaterialTheme.colorScheme.primary
                        } else if (heldKeys.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 6.dp
                    )
                    if (isComplete) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = if (heldKeys.isEmpty()) "..." else "${heldKeys.size}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Text(
                        text = if (isComplete) {
                            stringResource(R.string.settings_hotkeys_recording_saved)
                        } else if (heldKeys.isEmpty()) {
                            prompt
                        } else {
                            stringResource(R.string.settings_hotkeys_recording_holding)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (heldKeys.isNotEmpty() && !isComplete) {
                        Text(
                            text = HotkeyManager.formatCombo(heldKeys.toList()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
