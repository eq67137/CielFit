package com.cielfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cielfit.app.data.FitViewModel
import com.cielfit.app.domain.bmiLabel
import com.cielfit.app.domain.bmi
import com.cielfit.app.domain.pct
import com.cielfit.app.ui.components.CalorieRing
import com.cielfit.app.ui.components.MacroBar
import com.cielfit.app.ui.components.StatTile
import com.cielfit.app.ui.theme.CarbsRing
import com.cielfit.app.ui.theme.CarbsTeal
import com.cielfit.app.ui.theme.FatPurple
import com.cielfit.app.ui.theme.FatRing
import com.cielfit.app.ui.theme.ProteinBlue
import com.cielfit.app.ui.theme.ProteinRing
import com.cielfit.app.ui.theme.WaterBlue
import com.cielfit.app.ui.theme.WaterRing

@Composable
fun TodayScreen(vm: FitViewModel) {
    val totals by vm.dayTotals.collectAsStateWithLifecycle()
    val entries by vm.dayEntries.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()

    val targets = vm.macroTargets
    val waterGoal = vm.waterGoalMl

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Text("Today", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Track it. Adjust it.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CalorieRing(consumed = totals.kcal.toInt(), target = targets.kcal)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = "${(totals.protein).toInt()}g",
                    label = "Protein",
                    accent = ProteinBlue,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "${(totals.carbs).toInt()}g",
                    label = "Carbs",
                    accent = CarbsTeal,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "${(totals.fat).toInt()}g",
                    label = "Fat",
                    accent = FatPurple,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "${totals.waterMl}ml",
                    label = "Water",
                    accent = WaterBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Macros", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                MacroBar("Protein", totals.protein, targets.protein, "g", ProteinRing)
                MacroBar("Carbs", totals.carbs, targets.carbs, "g", CarbsRing)
                MacroBar("Fat", totals.fat, targets.fat, "g", FatRing)
            }
        }

        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Water", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${totals.waterMl} / $waterGoal ml",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(250, 500, 750).forEach { ml ->
                        Card(
                            onClick = { vm.addWater(ml) },
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+${ml}ml", fontSize = 13.sp, color = WaterBlue)
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val bmiValue = bmi(profile.weightKg, profile.heightCm)
                StatTile(
                    value = "%.1f".format(bmiValue),
                    label = bmiLabel(bmiValue),
                    accent = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "%.1f".format(profile.weightKg),
                    label = "Weight kg",
                    accent = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    value = "${(pct(totals.kcal, targets.kcal.toDouble()) * 100).toInt()}%",
                    label = "Of target",
                    accent = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                "Today's meals",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (entries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nothing logged yet", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "Hit Scan to photograph a meal or scan a barcode",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${entry.slot.replaceFirstChar { it.uppercase() }} · ${entry.grams.toInt()} g",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${entry.kcal.toInt()}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "kcal",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}