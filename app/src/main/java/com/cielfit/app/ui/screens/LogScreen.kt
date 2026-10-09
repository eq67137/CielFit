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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.cielfit.app.data.FitViewModel
import com.cielfit.app.data.MealEntry

@Composable
fun LogScreen(vm: FitViewModel) {
    val all by vm.allEntries.collectAsStateWithLifecycle()
    val todayTotals by vm.dayTotals.collectAsStateWithLifecycle()
    // Deleting a logged meal used to fire on any tap of the card. One stray
    // touch destroyed the entry with no undo, so the tap now opens a confirm.
    var pendingDelete by remember { mutableStateOf<MealEntry?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Log", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${all.size} entries in the last 30 days · today ${todayTotals.kcal.toInt()} kcal",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (all.isEmpty()) {
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
                        Text("No entries yet", fontWeight = FontWeight.Medium)
                        Text(
                            "Logged food shows up here",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(all, key = { it.id }) { entry ->
            Card(
                onClick = { pendingDelete = entry },
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
                            "${entry.slot} · ${entry.grams.toInt()}g · P${entry.protein.toInt()} C${entry.carbs.toInt()} F${entry.fat.toInt()}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text("${entry.kcal.toInt()} kcal", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete entry?") },
            text = { Text("${entry.name} · ${entry.grams.toInt()}g will be removed from ${entry.slot}.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEntry(entry.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}