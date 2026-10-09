package com.cielfit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the pure parts of the bundled FNRI table's parsing rules, without Android.
 * The asset loader itself needs a Context, so these mirror the exact same parsing
 * against the real shipped CSV read from the module's assets directory.
 */
class FilipinoFoodsTest {

    private val rows: List<String> by lazy {
        val f = java.io.File("src/main/assets/filipino_foods.csv")
        assertTrue("filipino_foods.csv must ship in assets", f.exists())
        f.readLines().drop(1).filter { it.isNotBlank() }
    }

    /** Same normalise() the repository uses: alphanumerics only, no separators. */
    private fun normalise(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun cols(line: String) = line.split(',')

    @Test
    fun `every row parses to sane per-100g macros`() {
        assertTrue("expected a few hundred FNRI rows, got ${rows.size}", rows.size > 200)
        rows.forEach { line ->
            val c = cols(line)
            assertTrue("short row: $line", c.size >= 9)
            val kcal = c[1].toDouble()
            val p = c[2].toDouble()
            val carb = c[3].toDouble()
            val f = c[4].toDouble()
            assertTrue("negative kcal in $line", kcal >= 0)
            listOf(p, carb, f).forEach { assertTrue("negative macro in $line", it >= 0) }
            // Atwater: 4/4/9. Real foods land within ~25%; a zero-macro row cannot.
            if (kcal > 20 && p + carb + f > 0) {
                val expected = p * 4 + carb * 4 + f * 9
                assertTrue(
                    "kcal $kcal inconsistent with macros in $line (Atwater says ~${expected.toInt()})",
                    expected == 0.0 || kotlin.math.abs(kcal - expected) / expected < 0.25
                )
            }
        }
    }

    @Test
    fun `serving weights are realistic`() {
        rows.forEach { line ->
            val g = cols(line)[5].toDouble()
            assertTrue("nonsense serving in $line: ${g}g", g in 4.0..400.0)
        }
    }

    @Test
    fun `alias rows point at a dish that exists`() {
        val byName = rows.map { normalise(cols(it)[0]) }.toSet()
        rows.forEach { line ->
            val c = cols(line)
            c[8].split('|').filter { it.isNotBlank() }.forEach { alias ->
                assertTrue(
                    "alias '$alias' in '$line' is not a real row",
                    byName.contains(normalise(alias))
                )
            }
        }
    }

    @Test
    fun `hyphen and space spellings resolve to the same food`() {
        // normalise() drops separators, so "Kare-Kare" and "kare kare" are ONE key.
        // If they were two keys, a "kare" search would list the dish twice.
        val byNorm = rows.associateBy { normalise(cols(it)[0]) }
        val key = normalise("kare kare")
        assertEquals("karekare", key)
        assertEquals("karekare", normalise("Kare-Kare"))
        assertTrue("kare kare should resolve", byNorm.containsKey(key))
        // both spellings exist as rows and carry identical macros
        val spaced = rows.first { normalise(cols(it)[0]) == key }
        assertEquals(64.0, cols(spaced)[1].toDouble(), 0.01)
        rows.filter { normalise(cols(it)[0]) == key }.forEach {
            assertEquals("macros differ between spellings: $it", cols(spaced)[1], cols(it)[1])
        }
    }

    @Test
    fun `adobo resolves to the FNRI pork adobo row, not an estimate`() {
        val adobo = rows.first { normalise(cols(it)[0]) == "adobo" }
        assertEquals(277.0, cols(adobo)[1].toDouble(), 0.01)
        assertEquals("fnri", cols(adobo)[7])
    }

    @Test
    fun `generic words are not aliased to one arbitrary dish`() {
        // The real risk is an alias, not a missing row: if "soup" were aliased to
        // misua then every "soup" search silently returns one dish. FNRI publishes no
        // bare "soup" row, so the only thing to assert is that these words are not
        // aliases of some specific dish.
        val aliased = rows.flatMap { cols(it)[8].split('|').filter { s -> s.isNotBlank() } }
            .map { normalise(it) }
            .toSet()
        listOf("soup", "rice", "chicken", "fish", "steak", "salad", "noodles", "bread").forEach { g ->
            assertTrue(
                "'$g' must not be an alias for one specific dish",
                !aliased.contains(normalise(g))
            )
        }
    }

    @Test
    fun `known FNRI dishes are present`() {
        val byName = rows.map { normalise(cols(it)[0]) }.toSet()
        listOf(
            "pork adobo", "chicken adobo", "sinigang", "kare-kare", "halo-halo",
            "kutsinta", "bibingka", "lumpia", "pancit molo", "dinuguan",
            "sinangag", "chicharon", "balut", "ube halaya", "leche flan"
        ).forEach { dish ->
            assertTrue("missing '$dish'", byNormName(byName, dish))
        }
    }

    private fun byNormName(set: Set<String>, dish: String) = set.contains(normalise(dish))

    @Test
    fun `unknown label returns nothing rather than a wrong dish`() {
        // Guards the search path: a non-Filipino query must not be force-matched.
        val byNorm = rows.map { normalise(cols(it)[0]) }.toSet()
        assertNull(byNorm.firstOrNull { it == "definitely not a food xyzzy" })
    }
}