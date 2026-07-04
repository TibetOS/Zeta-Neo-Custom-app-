package com.traffko.outlanderhub.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.traffko.outlanderhub.ui.theme.Hue
import com.traffko.outlanderhub.ui.theme.Micro

/**
 * The one panel style used everywhere: a barely-there vertical sheen over a
 * near-black surface, edged with a hairline. No elevation shadows — depth
 * comes from the stroke, like Tesla's night UI.
 */
fun Modifier.glassPanel(
    corner: Dp = 22.dp,
    fill: Color = Hue.Panel,
    stroke: Color = Hue.Hairline,
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0f to fill.copy(alpha = 0.96f),
                0.5f to fill,
                1f to Color(0xFF0B0D11),
            )
        )
        .border(1.dp, stroke, shape)
}

/** Press feedback: the whole element gently sinks, no ripple. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(stiffness = 900f),
        label = "press",
    )
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier, color: Color = Hue.TextSecondary) {
    Text(
        text.uppercase(),
        style = Micro,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Slowly breathing status dot, used for live/alert indicators. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Box(
        modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}
