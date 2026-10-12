package com.cielfit.app.data

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cielfit.app.domain.Activity
import com.cielfit.app.domain.Goal
import com.cielfit.app.domain.Macros
import com.cielfit.app.domain.Sex
import com.cielfit.app.domain.bmi
import com.cielfit.app.domain.calorieTarget
import com.cielfit.app.domain.macroSplit
import com.cielfit.app.ml.FoodClassifier
import com.cielfit.app.ml.Prediction
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class Profile(
    val sex: Sex = Sex.MALE,
    val age: Int = 30,
    val heightCm: Double = 175.0,
    val weightKg: Double = 70.0,
    val activity: Activity = Activity.MODERATE,
    val goal: Goal = Goal.CUT
)

data class DayTotals(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val waterMl: Int = 0
)

data class ScanCandidate(
    val label: String,
    val probability: Float,
    val nutrition: Nutrition? = null,
    val loading: Boolean = false,
    val failed: Boolean = false
)

class FitViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val repo = FoodRepository(app)

    private val _profile = MutableStateFlow(Profile())
    private val _dayTotals = MutableStateFlow(DayTotals())
    private val _dayEntries = MutableStateFlow<List<MealEntry>>(emptyList())
    private val _bodyMetrics = MutableStateFlow<List<BodyMetric>>(emptyList())
    private val _predictions = MutableStateFlow<List<ScanCandidate>>(emptyList())
    private val _classifierStatus = MutableStateFlow("idle")
    private val _allEntries = MutableStateFlow<List<MealEntry>>(emptyList())

    val profile: StateFlow<Profile> = _profile.asStateFlow()
    val dayTotals: StateFlow<DayTotals> = _dayTotals.asStateFlow()
    val dayEntries: StateFlow<List<MealEntry>> = _dayEntries.asStateFlow()
    val bodyMetrics: StateFlow<List<BodyMetric>> = _bodyMetrics.asStateFlow()
    val predictions: StateFlow<List<ScanCandidate>> = _predictions.asStateFlow()
    val classifierStatus: StateFlow<String> = _classifierStatus.asStateFlow()
    val allEntries: StateFlow<List<MealEntry>> = _allEntries.asStateFlow()

    private val _workoutSessions = MutableStateFlow<List<WorkoutSession>>(emptyList())
    val workoutSessions: StateFlow<List<WorkoutSession>> = _workoutSessions.asStateFlow()

    private val _exerciseSearchResults = MutableStateFlow<List<Exercise>>(emptyList())
    val exerciseSearchResults: StateFlow<List<Exercise>> = _exerciseSearchResults.asStateFlow()

    private val _bundlingExercises = MutableStateFlow(false)
    val bundlingExercises: StateFlow<Boolean> = _bundlingExercises.asStateFlow()

    private var classifier: FoodClassifier? = null

    val macroTargets: Macros
        get() {
            val p = _profile.value
            val t = calorieTarget(
                com.cielfit.app.domain.bmr(p.weightKg, p.heightCm, p.age, p.sex),
                p.activity, p.goal
            )
            return macroSplit(t, p.weightKg, p.goal)
        }

    val calorieGoal: Int get() = macroTargets.kcal
    val waterGoalMl: Int get() = 2500

    init {
        loadProfile()
        refreshToday()
        refreshHistory()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val d = db.settingDao()
            val p = Profile(
                sex = d.get("sex")?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: Sex.MALE,
                age = d.get("age")?.toIntOrNull() ?: 30,
                heightCm = d.get("height")?.toDoubleOrNull() ?: 175.0,
                weightKg = d.get("weight")?.toDoubleOrNull() ?: 70.0,
                activity = d.get("activity")?.let { runCatching { Activity.valueOf(it) }.getOrNull() }
                    ?: Activity.MODERATE,
                goal = d.get("goal")?.let { runCatching { Goal.valueOf(it) }.getOrNull() } ?: Goal.CUT
            )
            _profile.value = p
        }
    }

    fun updateProfile(p: Profile) {
        _profile.value = p
        viewModelScope.launch {
            val d = db.settingDao()
            d.put(Setting("sex", p.sex.name))
            d.put(Setting("age", p.age.toString()))
            d.put(Setting("height", p.heightCm.toString()))
            d.put(Setting("weight", p.weightKg.toString()))
            d.put(Setting("activity", p.activity.name))
            d.put(Setting("goal", p.goal.name))
        }
    }

    private fun dayRange(millis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val from = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return from to cal.timeInMillis
    }

    fun refreshToday(millis: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val (from, to) = dayRange(millis)
            val entries = db.mealDao().entriesForDayOnce(from, to)
            _dayEntries.value = entries
            _dayTotals.value = DayTotals(
                kcal = entries.sumOf { it.kcal },
                protein = entries.sumOf { it.protein },
                carbs = entries.sumOf { it.carbs },
                fat = entries.sumOf { it.fat },
                waterMl = db.waterDao().totalForDayOnce(from, to)
            )
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _bodyMetrics.value = db.bodyDao().allAsc().reversed()
            val since = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            _allEntries.value = db.mealDao().entriesInRange(since, System.currentTimeMillis())
        }
    }

    // ---------- scanning ----------

    fun classify(bitmap: Bitmap) {
        _classifierStatus.value = "loading"
        viewModelScope.launch {
            val clf = withContext(Dispatchers.IO) {
                classifier ?: runCatching { FoodClassifier.load(getApplication()) }.getOrNull()
                    ?.also { classifier = it }
            }
            if (clf == null) {
                _classifierStatus.value = "failed"
                bitmap.recycle()
                return@launch
            }
            _classifierStatus.value = "ready"
            val preds = try {
                withContext(Dispatchers.Default) { clf.classify(bitmap, topK = 5) }
            } catch (e: Exception) {
                android.util.Log.e("CielFit", "classification failed", e)
                _classifierStatus.value = "failed"
                emptyList()
            } finally {
                // The camera hands us a full-res frame we own; free it rather than
                // waiting for GC, or repeated scans climb into the heap.
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            _predictions.value = preds.map { ScanCandidate(it.label, it.probability, loading = true) }
            // resolve nutrition for each candidate in parallel-ish
            _predictions.value.forEachIndexed { index, cand ->
                viewModelScope.launch {
                    val n = repo.nutritionForLabel(cand.label)
                    _predictions.value = _predictions.value.toMutableList().also { list ->
                        list[index] = cand.copy(nutrition = n, loading = false, failed = n == null)
                    }
                }
            }
        }
    }

    fun searchFoods(query: String) {
        viewModelScope.launch {
            if (query.length < 2) { _predictions.value = emptyList(); return@launch }
            // Bundled FNRI dishes first - they resolve locally with real FNRI values,
            // while a label hit still needs a USDA round-trip.
            val local = repo.searchFilipinoFoods(query)
                .map { it.name }
                .ifEmpty {
                    repo.allLabelNames()
                        .filter { it.contains(query, ignoreCase = true) }
                        .take(20)
                }
            _predictions.value = local.map { ScanCandidate(it, 0f, loading = true) }
            local.forEachIndexed { index, name ->
                viewModelScope.launch {
                    val n = repo.nutritionForLabel(name)
                    _predictions.value = _predictions.value.toMutableList().also { list ->
                        if (index < list.size) {
                            list[index] = list[index].copy(nutrition = n, loading = false, failed = n == null)
                        }
                    }
                }
            }
        }
    }

    fun lookupBarcode(code: String) {
        viewModelScope.launch {
            val n = repo.nutritionForBarcode(code)
            _predictions.value = if (n != null) {
                listOf(ScanCandidate(n.name, 1f, n, loading = false))
            } else {
                listOf(ScanCandidate("Barcode $code", 0f, null, loading = false, failed = true))
            }
        }
    }

    fun clearCandidates() { _predictions.value = emptyList() }

    // ---------- logging ----------

    fun addFood(candidate: ScanCandidate, grams: Double, slot: String) {
        val n = candidate.nutrition ?: return
        viewModelScope.launch {
            val foodId = db.foodDao().byExternalId("label:${candidate.label}")?.id
                ?: db.foodDao().insert(
                    FoodItem(
                        externalId = "label:${candidate.label}",
                        name = candidate.label,
                        kcalPer100 = n.kcalPer100, proteinPer100 = n.proteinPer100,
                        carbsPer100 = n.carbsPer100, fatPer100 = n.fatPer100,
                        fiberPer100 = n.fiberPer100, sugarPer100 = n.sugarPer100,
                        sodiumMgPer100 = n.sodiumMgPer100, servingG = n.servingG,
                        source = n.source
                    )
                )
            db.mealDao().add(
                MealEntry(
                    dateMillis = System.currentTimeMillis(),
                    slot = slot,
                    foodId = foodId,
                    name = candidate.label,
                    grams = grams,
                    kcal = n.kcalPer100 * grams / 100.0,
                    protein = n.proteinPer100 * grams / 100.0,
                    carbs = n.carbsPer100 * grams / 100.0,
                    fat = n.fatPer100 * grams / 100.0
                )
            )
            _predictions.value = emptyList()
            refreshToday()
            refreshHistory()
        }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            db.mealDao().delete(id)
            refreshToday()
            refreshHistory()
        }
    }

    fun addWater(ml: Int) {
        viewModelScope.launch {
            db.waterDao().add(WaterLog(dateMillis = System.currentTimeMillis(), ml = ml))
            refreshToday()
        }
    }

    fun logBody(metric: BodyMetric) {
        viewModelScope.launch {
            db.bodyDao().add(metric.copy(dateMillis = System.currentTimeMillis()))
            val w = metric.weightKg
            if (w > 0) updateProfile(_profile.value.copy(weightKg = w))
            refreshHistory()
            refreshToday()
        }
    }

    // ---------- workout tracker ----------

    fun loadWorkouts() {
        viewModelScope.launch {
            _workoutSessions.value = db.workoutDao().allSessionsOnce()
        }
    }

    fun searchExercises(query: String) {
        viewModelScope.launch {
            if (query.length < 2) { _exerciseSearchResults.value = emptyList(); return@launch }
            _exerciseSearchResults.value = db.exerciseDao().search(query)
        }
    }

    fun clearExerciseSearch() { _exerciseSearchResults.value = emptyList() }

    suspend fun getExercise(id: Long): Exercise? = db.exerciseDao().byExternalId(id.toString())

    fun addWorkoutSession(name: String, notes: String, durationMin: Int, estimatedKcal: Double) {
        viewModelScope.launch {
            val id = db.workoutDao().addSession(
                WorkoutSession(
                    dateMillis = System.currentTimeMillis(),
                    name = name,
                    notes = notes,
                    durationMin = durationMin,
                    estimatedKcal = estimatedKcal
                )
            )
            loadWorkouts()
        }
    }

    fun addWorkoutSet(sessionId: Long, exerciseId: Long, exerciseName: String, setNum: Int, reps: Int, weightKg: Double) {
        viewModelScope.launch {
            db.workoutDao().addSet(
                WorkoutSet(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    exerciseName = exerciseName,
                    setNum = setNum,
                    reps = reps,
                    weightKg = weightKg
                )
            )
        }
    }

    fun deleteWorkoutSession(id: Long) {
        viewModelScope.launch {
            db.workoutDao().deleteSetsForSession(id)
            db.workoutDao().deleteSession(id)
            loadWorkouts()
        }
    }

    suspend fun getWorkoutSets(sessionId: Long): List<WorkoutSet> = db.workoutDao().setsForSession(sessionId)

    fun bundleExercisesIfNeeded() {
        viewModelScope.launch {
            if (db.exerciseDao().count() > 0) return@launch
            _bundlingExercises.value = true
            try {
                val json = getApplication<Application>().assets.open("exercises.json").bufferedReader().use { it.readText() }
                val arr = JSONArray(json)
                val list = mutableListOf<Exercise>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        Exercise(
                            externalId = o.optString("id", ""),
                            name = o.optString("name", ""),
                            muscle = o.optString("muscle", ""),
                            equipment = o.optString("equipment", ""),
                            instructions = o.optString("instructions", "")
                        )
                    )
                }
                db.exerciseDao().upsertAll(list)
            } catch (e: Exception) {
                android.util.Log.e("CielFit", "exercise bundle failed", e)
            } finally {
                _bundlingExercises.value = false
            }
        }
    }

    /** Estimate calories burned: MET × weight(kg) × duration(hr) */
    fun estimateCaloriesBurned(met: Double, weightKg: Double, durationMin: Int): Double {
        return met * weightKg * (durationMin / 60.0)
    }

    override fun onCleared() {
        classifier?.close()
        super.onCleared()
    }
}