package com.cielfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cielfit.app.data.FitViewModel
import com.cielfit.app.data.Profile
import com.cielfit.app.domain.Activity
import com.cielfit.app.domain.Goal
import com.cielfit.app.domain.Sex
import com.cielfit.app.domain.bmr
import com.cielfit.app.ui.components.MacroBar
import com.cielfit.app.ui.theme.CarbsRing
import com.cielfit.app.ui.theme.FatRing
import com.cielfit.app.ui.theme.ProteinRing

@Composable
fun PlanScreen(vm: FitViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var weight by remember(profile) { mutableStateOf(profile.weightKg.toString()) }
    var height by remember(profile) { mutableStateOf(profile.heightCm.toString()) }
    var age by remember(profile) { mutableStateOf(profile.age.toString()) }

    val w = weight.toDoubleOrNull() ?: 0.0
    val h = height.toDoubleOrNull() ?: 0.0
    val a = age.toIntOrNull() ?: 0
    val valid = w > 0 && h > 0 && a > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Plan", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Your targets", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)

                Text("Sex", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sex.entries.forEach { s ->
                        FilterChip(
                            selected = profile.sex == s,
                            onClick = { vm.updateProfile(profile.copy(sex = s)) },
                            label = { Text(s.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Text("Goal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Goal.entries.forEach { g ->
                        FilterChip(
                            selected = profile.goal == g,
                            onClick = { vm.updateProfile(profile.copy(goal = g)) },
                            label = { Text(g.label) }
                        )
                    }
                }

                Text("Activity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Activity.entries.forEach { act ->
                        FilterChip(
                            selected = profile.activity == act,
                            onClick = { vm.updateProfile(profile.copy(activity = act)) },
                            label = { Text(act.label) }
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("About you", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = weight, onValueChange = { weight = it },
                    label = { Text("Weight (kg)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = height, onValueChange = { height = it },
                    label = { Text("Height (cm)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = age, onValueChange = { age = it },
                    label = { Text("Age") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        vm.updateProfile(
                            profile.copy(
                                weightKg = w, heightCm = h, age = a
                            )
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save") }
            }
        }

        if (valid) {
            val targets = vm.macroTargets
            val bmrValue = bmr(w, h, a, profile.sex)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Calculated", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "BMR ${bmrValue.toInt()} kcal · TDEE ${(bmrValue * profile.activity.multiplier).toInt()} kcal",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Daily target ${targets.kcal} kcal",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    MacroBar("Protein", 0.0, targets.protein, "g", ProteinRing)
                    MacroBar("Carbs", 0.0, targets.carbs, "g", CarbsRing)
                    MacroBar("Fat", 0.0, targets.fat, "g", FatRing)
                }
            }
        }
    }
}