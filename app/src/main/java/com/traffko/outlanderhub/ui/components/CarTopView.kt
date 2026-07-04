package com.traffko.outlanderhub.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.traffko.outlanderhub.ui.theme.Hue
import com.traffko.outlanderhub.vehicle.DoorState

/**
 * Tesla-style top-down vehicle rendering, drawn entirely in Canvas so it stays
 * crisp at any size and cheap on the head unit's GPU. Nose points up. Open
 * doors swing outward with an animated flap and a red glow; open hood/trunk
 * tint their body section.
 */
@Composable
fun CarTopView(doors: DoorState, modifier: Modifier = Modifier) {
    val fl by doorAnim(doors.frontLeft)
    val fr by doorAnim(doors.frontRight)
    val rl by doorAnim(doors.rearLeft)
    val rr by doorAnim(doors.rearRight)
    val trunk by doorAnim(doors.trunk)
    val hood by doorAnim(doors.hood)

    // Reused across frames: the door-swing animation redraws every frame, and
    // allocating Paths inside DrawScope would churn the GC on the head unit.
    val bodyPath = remember { Path() }
    val bandPath = remember { Path() }

    Canvas(modifier) {
        val carL = size.height * 0.92f
        val carW = minOf(size.width * 0.46f, carL * 0.46f)
        val top = (size.height - carL) / 2f
        val left = (size.width - carW) / 2f
        val right = left + carW

        // Wheel stubs peeking out from the body (drawn one by one — no
        // per-frame list allocations in the render loop)
        val wheelW = carW * 0.14f
        val wheelH = carL * 0.13f
        drawWheelStub(Offset(left - wheelW * 0.62f, top + carL * 0.14f), wheelW, wheelH)
        drawWheelStub(Offset(right - wheelW * 0.38f, top + carL * 0.14f), wheelW, wheelH)
        drawWheelStub(Offset(left - wheelW * 0.62f, top + carL * 0.64f), wheelW, wheelH)
        drawWheelStub(Offset(right - wheelW * 0.38f, top + carL * 0.64f), wheelW, wheelH)

        // Body silhouette — softer nose, squarer tail
        val body = bodyPath.apply {
            reset()
            addRoundRect(
                RoundRect(
                    rect = Rect(left, top, right, top + carL),
                    topLeft = CornerRadius(carW * 0.46f, carL * 0.13f),
                    topRight = CornerRadius(carW * 0.46f, carL * 0.13f),
                    bottomLeft = CornerRadius(carW * 0.34f, carL * 0.07f),
                    bottomRight = CornerRadius(carW * 0.34f, carL * 0.07f),
                )
            )
        }
        drawPath(
            body,
            Brush.verticalGradient(
                0f to Color(0xFF262B32),
                0.45f to Color(0xFF1B1F25),
                1f to Color(0xFF14171C),
            ),
        )
        drawPath(body, Hue.HairlineBright, style = Stroke(width = 1.6f))

        // Hood tint when open
        if (hood > 0.01f) {
            drawRoundRect(
                color = Hue.Red.copy(alpha = 0.38f * hood),
                topLeft = Offset(left + 2f, top + 2f),
                size = Size(carW - 4f, carL * 0.2f),
                cornerRadius = CornerRadius(carW * 0.44f, carL * 0.12f),
            )
        }
        // Trunk tint when open
        if (trunk > 0.01f) {
            drawRoundRect(
                color = Hue.Red.copy(alpha = 0.38f * trunk),
                topLeft = Offset(left + 2f, top + carL * 0.84f),
                size = Size(carW - 4f, carL * 0.16f - 4f),
                cornerRadius = CornerRadius(carW * 0.3f, carL * 0.06f),
            )
        }

        // Glass: windshield, roof, rear window
        val glass = Color(0xFF0B0D10)
        drawGlassBand(bandPath, glass, left, right, top + carL * 0.24f, top + carL * 0.34f, carW, taperTop = 0.16f)
        drawRoundRect(
            color = Color(0xFF101318),
            topLeft = Offset(left + carW * 0.12f, top + carL * 0.36f),
            size = Size(carW * 0.76f, carL * 0.36f),
            cornerRadius = CornerRadius(carW * 0.14f),
        )
        drawGlassBand(bandPath, glass, left, right, top + carL * 0.74f, top + carL * 0.81f, carW, taperTop = -0.1f)

        // Door seams (unrolled: no per-frame list allocations)
        val seamColor = Color(0x33FFFFFF)
        val seamFront = top + carL * 0.335f
        val seamMid = top + carL * 0.52f
        val seamRear = top + carL * 0.70f
        drawLine(seamColor, Offset(left, seamFront), Offset(left + carW * 0.10f, seamFront), strokeWidth = 1.4f)
        drawLine(seamColor, Offset(right - carW * 0.10f, seamFront), Offset(right, seamFront), strokeWidth = 1.4f)
        drawLine(seamColor, Offset(left, seamMid), Offset(left + carW * 0.10f, seamMid), strokeWidth = 1.4f)
        drawLine(seamColor, Offset(right - carW * 0.10f, seamMid), Offset(right, seamMid), strokeWidth = 1.4f)
        drawLine(seamColor, Offset(left, seamRear), Offset(left + carW * 0.10f, seamRear), strokeWidth = 1.4f)
        drawLine(seamColor, Offset(right - carW * 0.10f, seamRear), Offset(right, seamRear), strokeWidth = 1.4f)

        // Mirrors
        val mirrorY = top + carL * 0.30f
        drawRoundRect(
            color = Color(0xFF20242B),
            topLeft = Offset(left - carW * 0.13f, mirrorY),
            size = Size(carW * 0.13f, carL * 0.035f),
            cornerRadius = CornerRadius(6f),
        )
        drawRoundRect(
            color = Color(0xFF20242B),
            topLeft = Offset(right, mirrorY),
            size = Size(carW * 0.13f, carL * 0.035f),
            cornerRadius = CornerRadius(6f),
        )

        // Swinging door flaps
        val doorLen = carL * 0.175f
        val thickness = carW * 0.085f
        drawDoorFlap(fl, hinge = Offset(left, seamFront), doorLen, thickness, leftSide = true)
        drawDoorFlap(fr, hinge = Offset(right, seamFront), doorLen, thickness, leftSide = false)
        drawDoorFlap(rl, hinge = Offset(left, seamMid), doorLen, thickness, leftSide = true)
        drawDoorFlap(rr, hinge = Offset(right, seamMid), doorLen, thickness, leftSide = false)
    }
}

@Composable
private fun doorAnim(open: Boolean) = animateFloatAsState(
    targetValue = if (open) 1f else 0f,
    animationSpec = tween(420),
    label = "door",
)

private fun DrawScope.drawWheelStub(topLeft: Offset, wheelW: Float, wheelH: Float) {
    drawRoundRect(
        color = Color(0xFF0C0E11),
        topLeft = topLeft,
        size = Size(wheelW, wheelH),
        cornerRadius = CornerRadius(wheelW * 0.45f),
    )
    drawRoundRect(
        color = Hue.Hairline,
        topLeft = topLeft,
        size = Size(wheelW, wheelH),
        cornerRadius = CornerRadius(wheelW * 0.45f),
        style = Stroke(width = 1.2f),
    )
}

private fun DrawScope.drawGlassBand(
    path: Path,
    color: Color,
    left: Float,
    right: Float,
    yTop: Float,
    yBottom: Float,
    carW: Float,
    taperTop: Float,
) {
    val inset = carW * 0.10f
    val taper = carW * taperTop
    path.reset()
    path.moveTo(left + inset + taper, yTop)
    path.lineTo(right - inset - taper, yTop)
    path.lineTo(right - inset, yBottom)
    path.lineTo(left + inset, yBottom)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawDoorFlap(
    swing: Float,
    hinge: Offset,
    doorLen: Float,
    thickness: Float,
    leftSide: Boolean,
) {
    if (swing < 0.01f) return
    // Red glow around the opening
    drawCircle(
        color = Hue.Red.copy(alpha = 0.16f * swing),
        radius = doorLen * 0.9f,
        center = Offset(hinge.x + (if (leftSide) -1 else 1) * thickness * 2f, hinge.y + doorLen / 2f),
    )
    val angle = (if (leftSide) -1 else 1) * 52f * swing
    rotate(degrees = angle, pivot = hinge) {
        drawRoundRect(
            color = Hue.Red.copy(alpha = 0.55f + 0.4f * swing),
            topLeft = Offset(if (leftSide) hinge.x - thickness else hinge.x, hinge.y),
            size = Size(thickness, doorLen),
            cornerRadius = CornerRadius(thickness * 0.5f),
        )
    }
}
