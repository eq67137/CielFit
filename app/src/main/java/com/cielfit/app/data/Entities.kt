package com.cielfit.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_item")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,          // barcode, or "label:<classId>" for ML foods
    val name: String,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val fiberPer100: Double = 0.0,
    val sugarPer100: Double = 0.0,
    val sodiumMgPer100: Double = 0.0,
    val servingG: Double = 100.0,
    val source: String               // "ml" | "barcode" | "usda" | "manual"
)

@Entity(tableName = "meal_entry")
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val slot: String,                // breakfast | lunch | dinner | snack
    val foodId: Long,                // FK -> food_item.id
    val name: String,                // denormalized so the log reads without a join
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

@Entity(tableName = "water_log")
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val ml: Int
)

@Entity(tableName = "body_metric")
data class BodyMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val weightKg: Double,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null
)

@Entity(tableName = "app_settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String
)

/** Cached USDA responses so a dish's nutrition is fetched once, then works offline. */
@Entity(tableName = "usda_cache")
data class UsdaCache(
    @PrimaryKey val queryKey: String,
    val payload: String,             // JSON
    val fetchedAt: Long
)

// --- Workout tracker (DB v2) -----------------------------------------------

@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,
    val name: String,
    val muscle: String,
    val equipment: String,
    val instructions: String
)

@Entity(tableName = "workout_session")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val name: String,
    val notes: String = "",
    val durationMin: Int = 0,
    val estimatedKcal: Double = 0.0
)

@Entity(tableName = "workout_set")
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String,        // denormalized so history reads without a join
    val setNum: Int,
    val reps: Int,
    val weightKg: Double
)