package com.cielfit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Self-check for the nutrition math. Plain JVM, no Android, no framework.
 * If BMR/TDEE/macro math ever breaks, this fails.
 */
class NutritionTest {

    @Test
    fun bmrMatchesMifflinStJeor() {
        // male, 80kg, 180cm, 30y -> 10*80 + 6.25*180 - 5*30 + 5 = 800+1125-150+5 = 1780
        assertEquals(1780.0, bmr(80.0, 180.0, 30, Sex.MALE), 0.01)
        // female, 65kg, 165cm, 28y -> 650 + 1031.25 - 140 - 161 = 1380.25
        assertEquals(1380.25, bmr(65.0, 165.0, 28, Sex.FEMALE), 0.01)
    }

    @Test
    fun tdeeAppliesActivityMultiplier() {
        assertEquals(1780.0 * 1.55, tdee(1780.0, Activity.MODERATE), 0.01)
    }

    @Test
    fun cutTargetSubtractsAndFloorsAtBmr() {
        val b = bmr(80.0, 180.0, 30, Sex.MALE)
        val t = calorieTarget(b, Activity.LIGHT, Goal.CUT)
        // 1780 * 1.375 - 500 = 1947.5 -> 1948, well above BMR so no floor
        assertEquals(1948, t)
        // a sedentary user cutting must never go below BMR
        val bmrVal = bmr(60.0, 150.0, 60, Sex.MALE)
        val floored = calorieTarget(bmrVal, Activity.SEDENTARY, Goal.CUT)
        assertTrue("target $floored must be >= BMR $bmrVal", floored >= bmrVal)
    }

    @Test
    fun macroSplitAddsUpToCalories() {
        val m = macroSplit(2000, 80.0, Goal.MAINTAIN)
        val fromMacros = m.protein * 4 + m.carbs * 4 + m.fat * 9
        // macros absorb the target within rounding tolerance
        assertEquals(2000.0, fromMacros, 12.0)
    }

    @Test
    fun proteinScalesWithGoal() {
        val cut = macroSplit(2000, 80.0, Goal.CUT).protein
        val maintain = macroSplit(2000, 80.0, Goal.MAINTAIN).protein
        val bulk = macroSplit(2000, 80.0, Goal.BULK).protein
        assertEquals(80.0 * 1.8, cut, 0.01)
        assertEquals(80.0 * 1.6, maintain, 0.01)
        assertEquals(80.0 * 2.0, bulk, 0.01)
        assertTrue(cut > maintain && maintain < bulk)
    }

    @Test
    fun progressAndPctClampToUnit() {
        assertEquals(0.5f, progress(500.0, 1000.0), 0.001f)
        assertEquals(1f, progress(1500.0, 1000.0), 0.001f) // over target clamps
        assertEquals(0f, progress(100.0, 0.0), 0.001f)      // zero target is safe
        assertEquals(1f, pct(120.0, 100.0), 0.001f)
    }

    @Test
    fun bmiAndLabels() {
        assertEquals(25.0, bmi(81.0, 180.0), 0.01)
        assertEquals("Healthy", bmiLabel(22.0))
        assertEquals("Overweight", bmiLabel(27.0))
        assertEquals("Obese", bmiLabel(33.0))
    }

    @Test
    fun scaleNutritionMultipliesPortion() {
        assertEquals(250.0, scaleNutrition(125.0, 200.0), 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bmrRejectsNonPositiveWeight() {
        bmr(0.0, 180.0, 30, Sex.MALE)
    }
}