package com.lomo.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn

object MotionTokens {
    const val DurationShort4 = 200
    const val DurationMedium1 = 250
    const val DurationMedium2 = 300
    const val DurationLong2 = 500

    val EasingStandard =
        androidx.compose.animation.core
            .CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val EasingEmphasized =
        androidx.compose.animation.core
            .CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EasingEmphasizedAccelerate =
        androidx.compose.animation.core
            .CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val EasingEmphasizedDecelerate =
        androidx.compose.animation.core
            .CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    val enterContent =
        fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))

    val exitContent = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
}
