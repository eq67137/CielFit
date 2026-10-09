package com.cielfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cielfit.app.data.BodyMetric
import com.cielfit.app.data.FitViewModel
import com.cielfit.app.domain.bmi
import com.cielfit.app.domain.bmiLabel
import com.cielfit.app.ui.components.StatTile
import com.cielfit.app.ui.theme.SuccessGreen

@Composable
fun BodyScreen(vm: FitViewModel) {
    val metrics by vm.bodyMetrics.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()

    var weight by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }

    val latest = metrics.lastOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Body", fontSize = 28.sp, fontWeight = FontWeight.Bold) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val w = latest?.weightKg ?: profile.weightKg
                StatTile("%.1f".format(w), "Weight kg", SuccessGreen, Modifier.weight(1f))
                val b = bmi(w, profile.heightCm)
                StatTile("%.1f".format(b), "BMI", MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
                StatTile(bmiLabel(b), "Rating", MaterialTheme.colorScheme.onBackground, Modifier.weight(1f))
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Log today", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text("Weight (kg)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = waist, onValueChange = { waist = it },
                        label = { Text("Waist (cm) — optional") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chest, onValueChange = { chest = it },
                        label = { Text("Chest (cm) — optional") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = arm, onValueChange = { arm = it },
                        label = { Text("Arm (cm) — optional") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = thigh, onValueChange = { thigh = it },
                        label = { Text("Thigh (cm) — optional") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val wv = weight.toDoubleOrNull()
                    Button(
                        onClick = {
                            if (wv != null && wv > 0) {
                                vm.logBody(
                                    BodyMetric(
                                        dateMillis = System.currentTimeMillis(),
                                        weightKg = wv,
                                        waistCm = waist.toDoubleOrNull(),
                                        chestCm = chest.toDoubleOrNull(),
                                        armCm = arm.toDoubleOrNull(),
                                        thighCm = thigh.toDoubleOrNull()
                                    )
                                )
                                weight = ""; waist = ""; chest = ""; arm = ""; thigh = ""
                            }
                        },
                        enabled = wv != null && wv > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save entry") }
                }
            }
        }

        item {
            Text("History", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        if (metrics.isEmpty()) {
            item {
                Text(
                    "No measurements yet",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(metrics.reversed().take(30), key = { it.id }) { m ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("%.1f kg".format(m.weightKg), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        val extras = listOfNotNull(
                            m.waistCm?.let { "waist ${"%.0f".format(it)}" },
                            m.chestCm?.let { "chest ${"%.0f".format(it)}" },
                            m.armCm?.let { "arm ${"%.0f".format(it)}" },
                            m.thighCm?.let { "thigh ${"%.0f".format(it)}" }
                        )
                        if (extras.isNotEmpty()) {
                            Text(
                                extras.joinToString(" · "),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                            .format(java.util.Date(m.dateMillis)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}