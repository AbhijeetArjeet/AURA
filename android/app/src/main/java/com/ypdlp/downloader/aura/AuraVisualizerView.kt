package com.ypdlp.downloader.aura

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ypdlp.downloader.VisualizerMode
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AuraVisualizerView(
    mode: VisualizerMode,
    bands: FloatArray,
    primaryColor: Color = Color(0xFFFF2A55),
    secondaryColor: Color = Color(0xFF00E5FF),
    modifier: Modifier = Modifier
) {
    if (mode == VisualizerMode.OFF) return

    val infiniteTransition = rememberInfiniteTransition(label = "visualizerAnim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f

        when (mode) {
            VisualizerMode.SPECTRUM -> {
                val barCount = bands.size.coerceAtMost(32)
                val barWidth = width / (barCount * 1.5f)
                val spacing = barWidth * 0.5f

                bands.take(barCount).forEachIndexed { i, value ->
                    val barHeight = (value * height * 0.85f).coerceAtLeast(4f)
                    val x = i * (barWidth + spacing) + spacing / 2f
                    val y = height - barHeight

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(primaryColor, secondaryColor)
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }
            }
            VisualizerMode.WAVEFORM -> {
                val path = Path()
                val stepX = width / bands.size
                path.moveTo(0f, centerY)

                bands.forEachIndexed { i, value ->
                    val x = i * stepX
                    val y = centerY + (sin(i.toDouble() * 0.8) * value * height * 0.4f).toFloat()
                    path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, primaryColor)),
                    style = Stroke(width = 5f)
                )
            }
            VisualizerMode.CIRCULAR -> {
                val baseRadius = (width.coerceAtMost(height) * 0.35f)
                val numPoints = bands.size
                val angleStep = (2 * Math.PI) / numPoints

                val path = Path()
                bands.forEachIndexed { i, value ->
                    val rad = Math.toRadians(rotation.toDouble()) + (i * angleStep)
                    val r = baseRadius + (value * 60f)
                    val x = (centerX + r * cos(rad)).toFloat()
                    val y = (centerY + r * sin(rad)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(
                    path = path,
                    brush = Brush.sweepGradient(listOf(primaryColor, secondaryColor, primaryColor)),
                    style = Stroke(width = 6f)
                )
            }
            VisualizerMode.PARTICLES -> {
                bands.take(24).forEachIndexed { i, value ->
                    val angle = (i * (360f / 24f)) + rotation
                    val rad = Math.toRadians(angle.toDouble())
                    val dist = (width.coerceAtMost(height) * 0.25f) + (value * 120f)
                    val px = (centerX + dist * cos(rad)).toFloat()
                    val py = (centerY + dist * sin(rad)).toFloat()
                    val radius = (value * 14f).coerceAtLeast(3f)

                    drawCircle(
                        color = if (i % 2 == 0) primaryColor else secondaryColor,
                        radius = radius,
                        center = Offset(px, py),
                        alpha = value.coerceIn(0.2f, 0.9f)
                    )
                }
            }
            VisualizerMode.MINIMAL -> {
                val waveHeight = (bands.take(10).average().toFloat() * 25f).coerceAtLeast(2f)
                drawLine(
                    brush = Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.2f), secondaryColor, primaryColor.copy(alpha = 0.2f))),
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = waveHeight
                )
            }
            VisualizerMode.OFF -> {}
        }
    }
}