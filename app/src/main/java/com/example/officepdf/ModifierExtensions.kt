package com.example.officepdf

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants

fun Modifier.bounceClick(
    scaleDown: Float = 0.95f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce"
    )

    val view = LocalView.current

    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
        )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedBounceClick(
    scaleDown: Float = 0.95f,
    enabled: Boolean = true,
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounce"
    )

    val view = LocalView.current

    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onDoubleClick = onDoubleClick,
            onLongClick = onLongClick,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
        )
}

