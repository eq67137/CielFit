package com.cielfit.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cielfit.app.data.BodyMetric
import com.cielfit.app.data.FitViewModel
import com.cielfit.app.ui.theme.CalorieOrange
import com.cielfit.app.ui.theme.Outline
import com.cielfit.app.ui.theme.RingColors
import com.cielfit.app.ui.theme.SuccessGreen
import kotlin.math.max
import kotlin.math.min

@Composable
fun TrendsScreen(vm: FitViewModel) {
    val metrics by vm.bodyMetrics.collectAsStateWithLifecycle()
    val entries by vm.allEntries.collectAsStateWithLifecycle()

    val topFoods = entries.groupingBy { it.name }.eachCount()
        .entries.sortedByDescending { it.value }.take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { Text("Trends", fontSize = 28.sp, fontWeight = FontWeight.Bold) }

        item {
            Column {
                Text("Weight", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (metrics.size < 2) {
                    Text(
                        "Log at least two weights to see the line",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    WeightChart(metrics)
                }
            }
        }

        item {
            Column {
                Text("Calorie adherence", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Last 7 logged days vs target",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val target = vm.calorieGoal
                AdherenceChart(entries, target)
            }
        }

        item {
            Column {
                Text("Most logged", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (topFoods.isEmpty()) {
                    Text(
                        "Nothing logged in the last 30 days",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        items(topFoods, key = { it.key }) { (name, count) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("$count×", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CalorieOrange)
                }
            }
        }
    }
}

@Composable
private fun WeightChart(metrics: List<BodyMetric>) {
    val weights = metrics.map { it.weightKg }
    val minW = weights.min()
    val maxW = weights.max()
    val range = (maxW - minW).coerceAtLeast(0.5)

    Column(modifier = Modifier.padding(top = 12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val w = size.width
            val h = size.height
            fun yFor(v: Double) = h - ((v - minW) / range * (h - 20f)).toFloat() - 10f

            // baseline grid
            drawLine(Outline, Offset(0f, h - 1f), Offset(w, h - 1f), 2f)

            val path = Path()
            weights.forEachIndexed { i, v ->
                val x = (i.toFloat() / max(1, weights.size - 1)) * w
                val y = yFor(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(SuccessGreen, 4f, Offset(x, y))
            }
            drawPath(
                path,
                brush = Brush.horizontalGradient(RingColors),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${"%.1f".format(minW)} kg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${"%.1f".format(maxW)} kg", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdherenceChart(entries: List<com.cielfit.app.data.MealEntry>, target: Int) {
    if (target <= 0 || entries.isEmpty()) {
        Text(
            "No data",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }
    val byDay = entries.groupBy {
        java.util.Calendar.getInstance().apply { timeInMillis = it.dateMillis }.timeInMillis / 86_400_000L
    }.entries.sortedByDescending { it.key }.take(7).reversed()

    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        byDay.forEach { (_, dayEntries) ->
            val kcal = dayEntries.sumOf { it.kcal }
            val frac = min(1f, (kcal / target).toFloat())
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${kcal.toInt()} kcal",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${(frac * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = if (frac in 0.85f..1.05f) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Outline, RoundedCornerShape(50))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .height(8.dp)
                            .background(
                                if (frac in 0.85f..1.05f) SuccessGreen else CalorieOrange,
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
    }
}