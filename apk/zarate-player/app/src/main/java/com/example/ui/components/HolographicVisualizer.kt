package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.CyberCellarColors
import com.example.ui.theme.LocalCyberCellarColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HolographicVisualizer(
    isPlaying: Boolean,
    style: String, // "wave", "particles", "spectrum"
    modifier: Modifier = Modifier
) {
    val themeColors = LocalCyberCellarColors.current

    // Animated phase for running waves
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 1500 else 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Animated intensity (pulsing beat)
    val pulseIntensity by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isPlaying) 400 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // 1. Draw Subterranean Wood Wine Barrel Boards
        drawOakBarrelBackground(themeColors, width, height)

        // 2. Draw Metal Vault Reinforcements & Copper Rivets
        drawMetalVaultFittings(themeColors, width, height)

        // 3. Draw Cyberpunk Holographic Scanlines
        drawHologramScanlines(width, height)

        // 4. Draw Animated Soundwaves / Particles / Spectrum
        when (style) {
            "wave" -> {
                drawHolographicWaves(themeColors, phase, pulseIntensity, isPlaying, width, height)
            }
            "particles" -> {
                drawWineFumesParticles(themeColors, phase, pulseIntensity, isPlaying, width, height)
            }
            "spectrum" -> {
                drawSpectralFrequencies(themeColors, phase, pulseIntensity, isPlaying, width, height)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOakBarrelBackground(
    colors: CyberCellarColors,
    w: Float,
    h: Float
) {
    val boardCount = 8
    val boardWidth = w / boardCount

    // Draw individual oak wooden staves
    for (i in 0 until boardCount) {
        val left = i * boardWidth
        val right = left + boardWidth

        // Curved oak grain representation
        val staveBrush = Brush.linearGradient(
            colors = listOf(
                colors.cellarWood.copy(alpha = 0.95f),
                colors.cellarWood.copy(alpha = 0.85f),
                colors.cellarWood.copy(alpha = 0.95f)
            ),
            start = Offset(left, 0f),
            end = Offset(right, 0f)
        )

        drawRect(
            brush = staveBrush,
            topLeft = Offset(left, 0f),
            size = Size(boardWidth, h)
        )

        // Draw vertical board cracks
        drawLine(
            color = Color.Black.copy(alpha = 0.85f),
            start = Offset(right, 0f),
            end = Offset(right, h),
            strokeWidth = 2.5f
        )
        drawLine(
            color = colors.secondaryAccent.copy(alpha = 0.15f),
            start = Offset(right + 2, 0f),
            end = Offset(right + 2, h),
            strokeWidth = 1f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMetalVaultFittings(
    colors: CyberCellarColors,
    w: Float,
    h: Float
) {
    // Top and Bottom steel horizontal reinforcing rings
    val ringHeight = 45f
    val ironBrushTop = Brush.verticalGradient(
        colors = listOf(colors.cellarMetal, Color.Black),
        startY = 0f,
        endY = ringHeight
    )
    val ironBrushBottom = Brush.verticalGradient(
        colors = listOf(Color.Black, colors.cellarMetal),
        startY = h - ringHeight,
        endY = h
    )

    // Top metal band
    drawRect(brush = ironBrushTop, topLeft = Offset(0f, 0f), size = Size(w, ringHeight))
    // Bottom metal band
    drawRect(brush = ironBrushBottom, topLeft = Offset(0f, h - ringHeight), size = Size(w, ringHeight))

    // Divider copper lines
    drawLine(colors.secondaryAccent.copy(alpha = 0.5f), Offset(0f, ringHeight), Offset(w, ringHeight), 1.5f)
    drawLine(colors.secondaryAccent.copy(alpha = 0.5f), Offset(0f, h - ringHeight), Offset(w, h - ringHeight), 1.5f)

    // Draw copper rivets/bolts along the top and bottom metallic plates
    val rivetSpacing = w / 10
    for (i in 0..10) {
        val rivetX = i * rivetSpacing + rivetSpacing / 2
        if (rivetX < w) {
            // Top Rivet
            drawCircle(
                color = colors.secondaryAccent,
                radius = 5f,
                center = Offset(rivetX, ringHeight / 2)
            )
            drawCircle(
                color = Color.Black,
                radius = 5.5f,
                center = Offset(rivetX, ringHeight / 2),
                style = Stroke(width = 1f)
            )
            // Bottom Rivet
            drawCircle(
                color = colors.secondaryAccent,
                radius = 5f,
                center = Offset(rivetX, h - ringHeight / 2)
            )
            drawCircle(
                color = Color.Black,
                radius = 5.5f,
                center = Offset(rivetX, h - ringHeight / 2),
                style = Stroke(width = 1f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHologramScanlines(
    w: Float,
    h: Float
) {
    val scanlineGap = 6f
    var y = 0f
    while (y < h) {
        drawLine(
            color = Color.Black.copy(alpha = 0.18f),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1.2f
        )
        y += scanlineGap
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHolographicWaves(
    colors: CyberCellarColors,
    phase: Float,
    pulse: Float,
    isPlaying: Boolean,
    w: Float,
    h: Float
) {
    val centerY = h / 2f
    val wavePathPrimary = Path()
    val wavePathSecondary = Path()

    wavePathPrimary.moveTo(0f, centerY)
    wavePathSecondary.moveTo(0f, centerY)

    val ampMultiplier = if (isPlaying) 65f * pulse else 12f
    val freqScale = 0.015f

    for (x in 0..w.toInt() step 5) {
        val xF = x.toFloat()
        // Wave 1: Primary neon glowing path
        val y1 = centerY + sin(xF * freqScale + phase) * ampMultiplier * cos(xF * 0.002f + phase * 0.5f).toFloat()
        wavePathPrimary.lineTo(xF, y1)

        // Wave 2: Complementary secondary color wave out of phase
        val y2 = centerY + cos(xF * freqScale * 0.8f - phase + 1f) * (ampMultiplier * 0.6f) * sin(xF * 0.001f - phase * 0.3f).toFloat()
        wavePathSecondary.lineTo(xF, y2)
    }

    // Neon glow effects using translucent strokes
    drawPath(
        path = wavePathPrimary,
        color = colors.primaryNeon.copy(alpha = 0.15f),
        style = Stroke(width = 14f, cap = StrokeCap.Round)
    )
    drawPath(
        path = wavePathPrimary,
        color = colors.primaryNeon,
        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
    )

    drawPath(
        path = wavePathSecondary,
        color = colors.secondaryAccent.copy(alpha = 0.2f),
        style = Stroke(width = 10f, cap = StrokeCap.Round)
    )
    drawPath(
        path = wavePathSecondary,
        color = colors.secondaryAccent,
        style = Stroke(width = 2f, cap = StrokeCap.Round)
    )

    // Draw central energy spark
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.primaryNeon, Color.Transparent),
            center = Offset(w / 2f, centerY),
            radius = 30f * pulse
        ),
        center = Offset(w / 2f, centerY),
        radius = 30f * pulse
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWineFumesParticles(
    colors: CyberCellarColors,
    phase: Float,
    pulse: Float,
    isPlaying: Boolean,
    w: Float,
    h: Float
) {
    // Generate organic bubbles floating up from the bottom representing champagne/wine fermentation
    val count = 25
    val seedX = listOf(
        0.1f, 0.25f, 0.38f, 0.45f, 0.52f, 0.68f, 0.77f, 0.89f, 0.95f, 0.18f,
        0.33f, 0.48f, 0.58f, 0.7f, 0.82f, 0.92f, 0.05f, 0.15f, 0.28f, 0.62f,
        0.75f, 0.85f, 0.5f, 0.4f, 0.3f
    )
    val seedY = listOf(
        0.8f, 0.75f, 0.6f, 0.9f, 0.4f, 0.85f, 0.5f, 0.3f, 0.95f, 0.2f,
        0.45f, 0.68f, 0.15f, 0.55f, 0.78f, 0.62f, 0.35f, 0.52f, 0.88f, 0.42f,
        0.18f, 0.28f, 0.72f, 0.65f, 0.58f
    )

    for (i in 0 until count) {
        val basePercentX = seedX[i]
        val basePercentY = seedY[i]

        // Animate Y position floating upward over time using phase
        val speedFactor = 0.15f + (i % 5) * 0.05f
        var currentYPercent = basePercentY - (phase * speedFactor) % 1.0f
        if (currentYPercent < 0f) currentYPercent += 1.0f

        // Add sideways sinusoidal wobble
        val wobble = sin(phase * 2f + i) * 12f * (if (isPlaying) pulse else 0.4f)
        val px = (basePercentX * w) + wobble
        val py = currentYPercent * (h - 90f) + 45f // stay inside metal plates

        val radius = (4f + (i % 6) * 1.5f) * (if (isPlaying) pulse else 1f)

        // Alternate colors between primary neon and wine accent
        val pColor = if (i % 2 == 0) colors.primaryNeon else colors.secondaryAccent
        val alpha = (0.2f + 0.6f * (1f - currentYPercent)).coerceIn(0f, 1f)

        // Draw particle bubble
        drawCircle(
            color = pColor.copy(alpha = alpha),
            radius = radius,
            center = Offset(px, py)
        )

        // Draw core highlight for glass glossy reflection
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.7f),
            radius = radius * 0.35f,
            center = Offset(px - radius * 0.3f, py - radius * 0.3f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectralFrequencies(
    colors: CyberCellarColors,
    phase: Float,
    pulse: Float,
    isPlaying: Boolean,
    w: Float,
    h: Float
) {
    val centerX = w / 2f
    val centerY = h / 2f

    // 1. Precise, heavy-hitting bass kick tracker using phase and pulse
    val kickVal = if (isPlaying) {
        val rawKick = Math.pow(kotlin.math.sin(phase.toDouble() * 3.5).coerceAtLeast(0.0), 4.0).toFloat()
        rawKick * pulse
    } else {
        0f
    }

    // 2. Ambient background radial light flash that expands on bass hit
    val flashRadius = (150f + 250f * kickVal) * (if (isPlaying) 1f else 0.4f)
    val flashAlpha = (0.05f + 0.28f * kickVal).coerceIn(0f, 0.45f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.primaryNeon.copy(alpha = flashAlpha), Color.Transparent),
            center = Offset(centerX, centerY),
            radius = flashRadius
        ),
        center = Offset(centerX, centerY),
        radius = flashRadius
    )

    // 3. Futuristic concentric holographic shockwave rings
    val ringCount = 3
    for (r in 0 until ringCount) {
        // Animate radius expanding outwards over time based on phase
        val ringPhase = (phase * 0.4f + r * 0.33f) % 1.0f
        val ringRadius = 50f + (w * 0.65f * ringPhase)
        // High expansion velocity on bass kicks
        val finalRingRadius = ringRadius + (60f * kickVal * ringPhase)
        val ringAlpha = ((1f - ringPhase) * 0.25f * (0.4f + 0.6f * kickVal)).coerceIn(0f, 0.45f)

        drawCircle(
            color = colors.primaryNeon.copy(alpha = ringAlpha),
            radius = finalRingRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.5f + 3f * kickVal)
        )
    }

    // 4. Procedural Cinematic Sparkle Stars (Destellos Épicos)
    // We render 18 sparkles scattered beautifully that drift, shimmer, and flare up on bass beats!
    val sparkCount = 18
    val seedX = listOf(
        0.15f, 0.35f, 0.78f, 0.55f, 0.24f, 0.85f, 0.48f, 0.66f, 0.92f, 0.12f,
        0.72f, 0.38f, 0.50f, 0.88f, 0.28f, 0.60f, 0.42f, 0.80f
    )
    val seedY = listOf(
        0.25f, 0.18f, 0.30f, 0.72f, 0.58f, 0.65f, 0.85f, 0.45f, 0.20f, 0.82f,
        0.12f, 0.38f, 0.50f, 0.88f, 0.48f, 0.28f, 0.62f, 0.54f
    )

    for (i in 0 until sparkCount) {
        val basePx = seedX[i] * w
        val basePy = seedY[i] * h

        // Gentle cosmic floating drift based on phase
        val driftSpeedX = if (i % 2 == 0) 15f else -15f
        val driftSpeedY = -25f - (i % 4) * 8f
        
        var px = basePx + sin(phase * 0.5f + i) * driftSpeedX
        // Float upwards over time
        var py = basePy + ((phase * driftSpeedY) % h)
        if (py < 0) py += h

        // Sparkle base size & brightness shimmer
        val shimmer = 0.5f + 0.5f * sin(phase * 4f + i)
        // Extreme responsive scaling under bass influence
        val bassSparkScale = 1.0f + 2.4f * kickVal
        val sparkSize = (6f + (i % 5) * 3f) * shimmer * bassSparkScale
        
        // Dynamic opacity: sparks glow intenser during playback and bass kicks
        val sparkAlpha = ((0.3f + 0.7f * shimmer) * (if (isPlaying) 0.4f + 0.6f * kickVal else 0.25f)).coerceIn(0.05f, 1.0f)
        val sparkColor = if (i % 3 == 0) colors.primaryNeon else colors.secondaryAccent

        // Draw Flare Core (Glow Spot)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(sparkColor.copy(alpha = sparkAlpha), Color.Transparent),
                center = Offset(px, py),
                radius = sparkSize * 1.5f
            ),
            center = Offset(px, py),
            radius = sparkSize * 1.5f
        )
        drawCircle(
            color = Color.White.copy(alpha = sparkAlpha * 0.8f),
            radius = sparkSize * 0.3f,
            center = Offset(px, py)
        )

        // Draw Cross Flare Lines (The "Destello" Sparkle Flare)
        // On bass hit, the laser flare lines stretch and expand outwards!
        val flareLength = sparkSize * (2.8f + 3f * kickVal)
        val flareThickness = (1.2f + 1.5f * kickVal).coerceAtLeast(1f)

        // Horizontal Flare Line
        drawLine(
            color = sparkColor.copy(alpha = sparkAlpha * 0.85f),
            start = Offset(px - flareLength, py),
            end = Offset(px + flareLength, py),
            strokeWidth = flareThickness
        )
        // Vertical Flare Line
        drawLine(
            color = sparkColor.copy(alpha = sparkAlpha * 0.85f),
            start = Offset(px, py - flareLength),
            end = Offset(px, py + flareLength),
            strokeWidth = flareThickness
        )

        // Draw diagonal micro-flares for high premium stars
        if (i % 2 == 0) {
            val diagLength = flareLength * 0.55f
            drawLine(
                color = colors.primaryNeon.copy(alpha = sparkAlpha * 0.6f),
                start = Offset(px - diagLength, py - diagLength),
                end = Offset(px + diagLength, py + diagLength),
                strokeWidth = flareThickness * 0.75f
            )
            drawLine(
                color = colors.primaryNeon.copy(alpha = sparkAlpha * 0.6f),
                start = Offset(px - diagLength, py + diagLength),
                end = Offset(px + diagLength, py - diagLength),
                strokeWidth = flareThickness * 0.75f
            )
        }
    }
}
