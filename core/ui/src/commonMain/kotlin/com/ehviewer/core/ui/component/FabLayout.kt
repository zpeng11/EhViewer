package com.ehviewer.core.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import arrow.resilience.Schedule
import arrow.resilience.retry
import com.ehviewer.core.ui.util.PredictiveBackEasing
import com.ehviewer.core.ui.util.asyncState
import com.ehviewer.core.ui.util.snackBarPadding
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.onEachLatest
import kotlin.time.Duration.Companion.microseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Stable
enum class FabLayoutValue { Hidden, Primary, Expand }

@Stable
@Composable
fun rememberFabLayoutState(initialValue: FabLayoutValue = FabLayoutValue.Primary) = remember {
    FabLayoutState(initialValue)
}

@Stable
class FabLayoutState(
    initialValue: FabLayoutValue,
    private val animationSpec: AnimationSpec<Float> = tween(FAB_ANIMATE_TIME),
) {
    val mutatorMutex = MutatorMutex()

    val appearProgress = Animatable(if (initialValue != FabLayoutValue.Hidden) 1f else 0f)
        .apply { updateBounds(0f, 1f) }

    val expandProgress = Animatable(if (initialValue == FabLayoutValue.Expand) 1f else 0f)
        .apply { updateBounds(0f, 1f) }

    private suspend fun downTo(value: FabLayoutValue, priority: MutatePriority) = mutatorMutex.mutate(priority) {
        if (expandProgress.value != 0f) {
            expandProgress.animateTo(0f, animationSpec)
        }
        if (value == FabLayoutValue.Hidden) {
            appearProgress.animateTo(0f, animationSpec)
        }
    }

    private suspend fun upTo(value: FabLayoutValue, priority: MutatePriority) = mutatorMutex.mutate(priority) {
        if (appearProgress.value != 1f) {
            appearProgress.animateTo(1f, animationSpec)
        }
        if (value == FabLayoutValue.Expand) {
            expandProgress.animateTo(1f, animationSpec)
        }
    }

    suspend fun show(priority: MutatePriority = MutatePriority.Default) = upTo(FabLayoutValue.Primary, priority)
    suspend fun hide(priority: MutatePriority = MutatePriority.Default) = downTo(FabLayoutValue.Hidden, priority)
    suspend fun collapse(priority: MutatePriority = MutatePriority.Default) = downTo(FabLayoutValue.Primary, priority)
    suspend fun expand(priority: MutatePriority = MutatePriority.Default) = upTo(FabLayoutValue.Expand, priority)
}

private val fabSyncSchedule = Schedule.linear<Throwable>(100.microseconds)

fun interface FabBuilder {
    fun onClick(icon: ImageVector, autoClose: Boolean, onLongClick: (suspend () -> Unit)?, that: suspend () -> Unit)
    fun onClick(icon: ImageVector, autoClose: Boolean, that: suspend () -> Unit) = onClick(icon, autoClose, null, that)
    fun onClick(icon: ImageVector, that: suspend () -> Unit) = onClick(icon, true, that)
}

@Composable
context(_: CoroutineScope)
fun FabLayout(
    hidden: Boolean,
    expanded: Boolean,
    onExpandChanged: (Boolean) -> Unit,
    autoCancel: Boolean,
    fabBuilder: FabBuilder.() -> Unit,
) {
    val updatedHidden by rememberUpdatedState(hidden)
    val updatedExpanded by rememberUpdatedState(expanded)
    val newState = when {
        updatedHidden -> FabLayoutValue.Hidden
        updatedExpanded -> FabLayoutValue.Expand
        else -> FabLayoutValue.Primary
    }
    val state = rememberFabLayoutState(newState)
    LaunchedEffect(newState) {
        fabSyncSchedule.retry {
            when (newState) {
                FabLayoutValue.Expand -> state.expand()
                FabLayoutValue.Hidden -> state.hide()
                FabLayoutValue.Primary -> {
                    state.collapse()
                    state.show()
                }
            }
        }
    }
    val builder by rememberUpdatedState(fabBuilder)

    val secondaryFab by asyncState(
        produce = { buildFab(builder) },
        transform = { onEachLatest { state.collapse(MutatePriority.PreventUserInput) } },
    )

    val density = LocalDensity.current
    val interval = remember(density) { with(density) { FabInterval.roundToPx() } }
    val padding = remember(density) { with(density) { FabPadding.roundToPx() } }

    if (updatedExpanded && autoCancel) {
        Spacer(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { onExpandChanged(false) }
            },
        )
    }

    val animatedProgress by state.expandProgress.asState()
    PredictiveBackHandler(updatedExpanded) { flow ->
        try {
            state.mutatorMutex.mutate(MutatePriority.UserInput) {
                flow.collect {
                    val eased = PredictiveBackEasing.transform(it.progress)
                    state.expandProgress.snapTo(1 - eased)
                }
            }
            onExpandChanged(false)
        } catch (_: CancellationException) {
            launch { state.expand() }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().navigationBarsPadding().snackBarPadding(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        val hapticFeedback = LocalHapticFeedback.current
        val indication = LocalIndication.current
        if (!state.appearProgress.isRunning && !updatedHidden) {
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = animatedProgress },
                contentAlignment = Alignment.BottomEnd,
            ) {
                with(secondaryFab) {
                    forEachIndexed { index, action ->
                        SecondaryFabButton(
                            icon = action.icon,
                            shape = CircleShape,
                            onClick = {
                                launch(Dispatchers.Default) {
                                    action.onClick()
                                    if (action.autoClose) onExpandChanged(false)
                                }
                            },
                            onLongClick = action.onLongClick?.let { longClick ->
                                {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    launch(Dispatchers.Default) {
                                        longClick()
                                        if (action.autoClose) onExpandChanged(false)
                                    }
                                }
                            },
                            indication = indication,
                            modifier = Modifier.padding(20.dp).offset {
                                val distance = lerp(0, interval * (size - index) + padding, animatedProgress)
                                IntOffset(0, -distance)
                            },
                        )
                    }
                }
            }
        }
        val appearState by state.appearProgress.asState()
        FloatingActionButton(
            onClick = { onExpandChanged(!updatedExpanded) },
            modifier = Modifier.padding(16.dp).graphicsLayer {
                rotationZ = lerp(-90f, 0f, appearState)
                scaleX = appearState
                scaleY = appearState
            },
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.graphicsLayer {
                    rotationZ = lerp(-135f, 0f, animatedProgress)
                },
            )
        }
    }
}

private fun buildFab(builder: FabBuilder.() -> Unit) = buildList {
    builder { icon, autoClose, onLongClick, action ->
        add(FabAction(icon, autoClose, action, onLongClick))
    }
}

private data class FabAction(
    val icon: ImageVector,
    val autoClose: Boolean,
    val onClick: suspend () -> Unit,
    val onLongClick: (suspend () -> Unit)? = null,
)

@Composable
private fun SecondaryFabButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    indication: androidx.compose.foundation.Indication? = null,
    shape: Shape = CircleShape,
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .combinedClickable(
                interactionSource = null,
                indication = indication,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null)
        }
    }
}

private val FabInterval = 56.dp // Small FAB height + 16 dp
private val FabPadding = 8.dp // (FAB height - small FAB height) / 2
const val FAB_ANIMATE_TIME = 300
