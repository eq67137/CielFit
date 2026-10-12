package com.cielfit.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cielfit.app.data.Exercise
import com.cielfit.app.data.FitViewModel
import com.cielfit.app.data.WorkoutSession
import com.cielfit.app.data.WorkoutSet

private val MET_VALUES = mapOf(
    "push-up" to 8.0, "pull-up" to 8.0, "squat" to 5.0, "lunge" to 5.0,
    "plank" to 3.0, "bench press" to 6.0, "deadlift" to 6.0,
    "overhead press" to 6.0, "barbell row" to 6.0, "dumbbell curl" to 4.0,
    "tricep dip" to 5.0, "running" to 9.8, "cycling" to 7.5,
    "swimming" to 8.0, "jump rope" to 11.0, "burpee" to 10.0,
    "kettlebell swing" to 9.0, "rowing" to 7.0
)

private fun metForExercise(name: String): Double {
    val lower = name.lowercase()
    MET_VALUES.forEach { (key, met) -> if (lower.contains(key)) return met }
    return 5.0
}

@Composable
fun WorkoutScreen(vm: FitViewModel) {
    val sessions by vm.workoutSessions.collectAsStateWithLifecycle()
    val exercises by vm.exerciseSearchResults.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val bundling by vm.bundlingExercises.collectAsStateWithLifecycle()

    var showAddSession by remember { mutableStateOf(false) }
    var expandedSession by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        vm.bundleExercisesIfNeeded()
        vm.loadWorkouts()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Workout", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = { showAddSession = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Log")
            }
        }

        if (bundling) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(16.dp)) {
                    Text("Loading exercises...")
                }
            }
        }

        if (sessions.isEmpty() && !bundling) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("No workouts yet", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Tap Log to record a session with sets, reps, and weight.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        sessions.forEach { session ->
            WorkoutSessionCard(
                session = session,
                expanded = expandedSession == session.id,
                onToggle = { expandedSession = if (expandedSession == session.id) null else session.id },
                vm = vm
            )
        }
    }

    if (showAddSession) {
        AddWorkoutDialog(
            vm = vm,
            exercises = exercises,
            weightKg = profile.weightKg,
            onDismiss = { showAddSession = false }
        )
    }
}

@Composable
private fun WorkoutSessionCard(
    session: WorkoutSession,
    expanded: Boolean,
    onToggle: () -> Unit,
    vm: FitViewModel
) {
    var sets by remember { mutableStateOf<List<WorkoutSet>>(emptyList()) }
    LaunchedEffect(expanded, session.id) {
        if (expanded) sets = vm.getWorkoutSets(session.id)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(session.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${session.durationMin} min · ${session.estimatedKcal.toInt()} kcal",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.deleteWorkoutSession(session.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }

            if (expanded && sets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                sets.groupBy { it.exerciseName }.forEach { (name, exSets) ->
                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    exSets.forEach { s ->
                        Text(
                            "  Set ${s.setNum}: ${s.reps} reps × ${s.weightKg.toInt()} kg",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TextButton(onClick = onToggle) {
                Text(if (expanded) "Hide sets" else "Show sets")
            }
        }
    }
}

@Composable
private fun AddWorkoutDialog(
    vm: FitViewModel,
    exercises: List<Exercise>,
    weightKg: Double,
    onDismiss: () -> Unit
) {
    var sessionName by remember { mutableStateOf("") }
    var exerciseQuery by remember { mutableStateOf("") }
    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var duration by remember { mutableStateOf("30") }
    val searchResults by vm.exerciseSearchResults.collectAsStateWithLifecycle()

    LaunchedEffect(exerciseQuery) { vm.searchExercises(exerciseQuery) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Workout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = { Text("Session name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = exerciseQuery,
                    onValueChange = { exerciseQuery = it },
                    label = { Text("Exercise") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (searchResults.isNotEmpty() && selectedExercise == null) {
                    Card(modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
                            searchResults.take(10).forEach { ex ->
                                TextButton(
                                    onClick = {
                                        selectedExercise = ex
                                        exerciseQuery = ex.name
                                        vm.clearExerciseSearch()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(ex.name, fontSize = 13.sp)
                                        Text(
                                            ex.muscle,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                    label = { Text("Duration (min)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                selectedExercise?.let { ex ->
                    val met = metForExercise(ex.name)
                    val dur = duration.toIntOrNull() ?: 30
                    val kcal = vm.estimateCaloriesBurned(met, weightKg, dur)
                    Text(
                        "Estimated: ${kcal.toInt()} kcal (MET $met)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dur = duration.toIntOrNull() ?: 30
                    val ex = selectedExercise
                    val met = if (ex != null) metForExercise(ex.name) else 5.0
                    val kcal = vm.estimateCaloriesBurned(met, weightKg, dur)
                    vm.addWorkoutSession(
                        name = sessionName.ifEmpty { ex?.name ?: "Workout" },
                        notes = ex?.name ?: "",
                        durationMin = dur,
                        estimatedKcal = kcal
                    )
                    onDismiss()
                },
                enabled = sessionName.isNotBlank() || selectedExercise != null
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
