package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun FuturisticSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableStateOf(0f) }
    
    val fraction = if (valueRange.endInclusive > valueRange.start) {
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (width > 0) {
                        val newFraction = (offset.x / width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (width > 0) {
                        val newFraction = (change.position.x / width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
            width = size.width
            val h = size.height
            val cy = h / 2f
            
            val thumbX = fraction * width

            // Inactive Track
            drawLine(
                color = Color(0xFF1E1E1E),
                start = Offset(0f, cy),
                end = Offset(width, cy),
                strokeWidth = 6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            
            // Active Track Gradient
            if (thumbX > 0) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(primaryColor, secondaryColor),
                        startX = 0f,
                        endX = thumbX
                    ),
                    start = Offset(0f, cy),
                    end = Offset(thumbX, cy),
                    strokeWidth = 6.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            
            // Glowing Thumb Frame
            drawRect(
                color = secondaryColor,
                topLeft = Offset(thumbX - 2.dp.toPx(), cy - 8.dp.toPx()),
                size = Size(4.dp.toPx(), 16.dp.toPx())
            )
            // Inner glow
            drawRect(
                color = Color.White,
                topLeft = Offset(thumbX - 0.5f.dp.toPx(), cy - 6.dp.toPx()),
                size = Size(1.dp.toPx(), 12.dp.toPx())
            )
        }
    }
}
