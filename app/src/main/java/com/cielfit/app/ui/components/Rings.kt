package com.cielfit.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cielfit.app.ui.theme.DangerRed
import com.cielfit.app.ui.theme.Outline
import com.cielfit.app.ui.theme.RingColors
import com.cielfit.app.ui.theme.SurfaceHigh

/** The hero calorie ring: gradient sweep + centered consumed/target. */
@Composable
fun CalorieRing(
    consumed: Int,
    target: Int,
    modifier: Modifier = Modifier,
    ringColors: List<Color> = RingColors,
    centerLabel: String = "remaining",
    size: androidx.compose.ui.unit.Dp = 210.dp
) {
    val targetF = target.coerceAtLeast(1).toFloat()
    val raw = (consumed.toFloat() / targetF).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = raw, animationSpec = tween(900), label = "ring"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val ringCenter = Offset(this.size.width / 2f, this.size.height / 2f)

            // track
            drawArc(
                color = Outline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // over-target indicator: if consumed exceeds target, draw the full ring in red
            if (consumed > target && target > 0) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(DangerRed.copy(alpha = 0.85f))),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            } else {
                // gradient progress arc
                val sweep = Brush.sweepGradient(ringColors, ringCenter)
                drawArc(
                    brush = sweep,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val remaining = (target - consumed).coerceAtLeast(0)
            Text(
                text = if (consumed > target) "+${consumed - target}" else "$remaining",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = centerLabel,
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$consumed / $target kcal",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Horizontal gradient progress bar used for macros and micronutrients. */
@Composable
fun MacroBar(
    label: String,
    value: Double,
    target: Double,
    unit: String,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val fraction = if (target <= 0) 0f else (value / target).coerceIn(0.0, 1.0).toFloat()
    val animated by animateFloatAsState(
        targetValue = fraction, animationSpec = tween(700), label = "bar"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${value.toInt()} / ${target.toInt()} $unit",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(50))
                .background(Outline)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(colors))
            )
        }
    }
}

/** Small labeled stat tile. */
@Composable
fun StatTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceHigh)
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = accent)
        Text(
            label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}