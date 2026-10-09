package com.cielfit.app.domain

/**
 * Pure nutrition math. No Android imports — unit-testable in plain JVM.
 * All functions take explicit values so nothing hides behind global state.
 */

enum class Sex { MALE, FEMALE }

enum class Activity(val multiplier: Double, val label: String) {
    SEDENTARY(1.2, "Sedentary — desk job, little exercise"),
    LIGHT(1.375, "Light — exercise 1-3 days/week"),
    MODERATE(1.55, "Moderate — exercise 3-5 days/week"),
    ACTIVE(1.725, "Active — exercise 6-7 days/week"),
    ATHLETE(1.9, "Athlete — twice daily or physical job")
}

enum class Goal(val kcalAdj: Double, val label: String) {
    CUT(-500.0, "Lose weight"),
    MAINTAIN(0.0, "Maintain weight"),
    BULK(400.0, "Gain muscle")
}

data class Macros(val kcal: Int, val protein: Double, val carbs: Double, val fat: Double)

/** Mifflin-St Jeor basal metabolic rate, kcal/day. */
fun bmr(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double {
    require(weightKg > 0) { "weight must be positive" }
    require(heightCm > 0) { "height must be positive" }
    require(ageYears > 0) { "age must be positive" }
    val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears
    return if (sex == Sex.MALE) base + 5 else base - 161
}

/** Total daily energy expenditure = BMR x activity multiplier. */
fun tdee(bmrValue: Double, activity: Activity): Double = bmrValue * activity.multiplier

/** Daily calorie target after the goal adjustment, floored at BMR so it never starves. */
fun calorieTarget(bmrValue: Double, activity: Activity, goal: Goal): Int {
    val raw = (tdee(bmrValue, activity) + goal.kcalAdj)
        .coerceAtLeast(bmrValue) // ponytail: hard floor at BMR; a deficit below BMR is unsafe
    return Math.round(raw).toInt()
}

/**
 * Macro grams from a calorie target.
 * Protein grams from bodyweight (1.8g/kg cut, 1.6 maintain, 2.0 bulk), fat = 25% of kcal,
 * carbs take whatever kcal remain.
 */
fun macroSplit(kcal: Int, weightKg: Double, goal: Goal): Macros {
    require(kcal > 0) { "kcal must be positive" }
    require(weightKg > 0) { "weight must be positive" }
    val proteinPerKg = when (goal) {
        Goal.CUT -> 1.8
        Goal.MAINTAIN -> 1.6
        Goal.BULK -> 2.0
    }
    val protein = weightKg * proteinPerKg
    val fat = kcal * 0.25 / 9.0
    val carbs = (kcal - protein * 4 - fat * 9).coerceAtLeast(0.0) / 4.0
    return Macros(kcal, protein, carbs, fat)
}

/** Progress toward a target, clamped to 0..1 for ring rendering. */
fun progress(current: Double, target: Double): Float {
    if (target <= 0.0) return 0f
    return (current / target).coerceIn(0.0, 1.0).toFloat()
}

fun pct(part: Double, whole: Double): Float =
    if (whole <= 0.0) 0f else (part / whole).coerceIn(0.0, 1.0).toFloat()

fun bmi(weightKg: Double, heightCm: Double): Double {
    require(heightCm > 0) { "height must be positive" }
    val m = heightCm / 100.0
    return weightKg / (m * m)
}

fun bmiLabel(value: Double): String = when {
    value < 18.5 -> "Underweight"
    value < 25.0 -> "Healthy"
    value < 30.0 -> "Overweight"
    else -> "Obese"
}

/** Scale a food's per-100g nutrition to an actual portion in grams. */
fun scaleNutrition(per100: Double, grams: Double): Double = per100 * grams / 100.0