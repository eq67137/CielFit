package com.cielfit.app.data

import android.content.Context
import com.cielfit.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Nutrition(
    val name: String,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val fiberPer100: Double = 0.0,
    val sugarPer100: Double = 0.0,
    val sodiumMgPer100: Double = 0.0,
    val servingG: Double = 100.0,
    val source: String = "manual"
)

/**
 * Single entry point for nutrition lookups. Screens never touch TFLite or HTTP directly.
 * Order: local cache -> bundled FNRI Filipino foods -> USDA -> keyword estimate.
 * Everything that resolves is written to Room so a repeat lookup is offline.
 */
class FoodRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val labels: List<String> by lazy {
        appContext.assets.open("food_labels.csv").bufferedReader().useLines { lines ->
            lines.drop(1).map { it.substringAfter(',').trim() }.toList()
        }
    }

    /**
     * Bundled FNRI Philippine Food Composition Table rows, keyed by normalised name.
     * Authoritative measured values, so they outrank the USDA network call - USDA has
     * no authentic Filipino dishes at all. ponytail: exact + alias match only. Add
     * fuzzy/typo tolerance if search misses become a complaint.
     */
    private val filipinoFoods: Map<String, Nutrition> by lazy {
        val out = HashMap<String, Nutrition>()
        appContext.assets.open("filipino_foods.csv").bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val c = line.split(',')
                if (c.size < 8) return@forEach
                val nutrition = Nutrition(
                    name = c[0],
                    kcalPer100 = c[1].toDouble(),
                    proteinPer100 = c[2].toDouble(),
                    carbsPer100 = c[3].toDouble(),
                    fatPer100 = c[4].toDouble(),
                    servingG = c[5].toDouble(),
                    source = c[7]
                )
                out[normalise(c[0])] = nutrition
                // aliases let "adobo" and "adobo chicken" reach a specific dish row
                c[8].split('|').filter { it.isNotBlank() }.forEach { a ->
                    out.putIfAbsent(normalise(a), nutrition.copy(name = a))
                }
            }
        }
        out
    }

    /**
     * Strip everything non-alphanumeric, so "Kare-Kare", "kare kare" and "karekare"
     * are one key. Without this, hyphen and space spellings of the same dish resolve
     * to two keys and search lists the dish twice.
     */
    private fun normalise(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** Every bundled ML class name, for local search without a network call. */
    fun allLabelNames(): List<String> = labels

    /** Filipino dishes that match free text, so search reaches them without a scan. */
    fun searchFilipinoFoods(query: String): List<Nutrition> {
        val q = normalise(query)
        if (q.isEmpty()) return emptyList()
        return filipinoFoods.values
            // dedupe on the normalised key: aliases share one key, so one row each
            .distinctBy { normalise(it.name) }
            .filter { normalise(it.name).contains(q) }
            .sortedBy { it.name }
            .take(20)
    }

    suspend fun nutritionForLabel(label: String): Nutrition? = withContext(Dispatchers.IO) {
        val cached = db.foodDao().byExternalId("label:$label")
        if (cached != null) return@withContext cached.toNutrition()
        val nutrition = filipinoFoods[normalise(label)]
            ?: lookupUsda(label)
            ?: estimateFromLabel(label)
        db.foodDao().insert(
            FoodItem(
                externalId = "label:$label",
                name = label,
                kcalPer100 = nutrition.kcalPer100,
                proteinPer100 = nutrition.proteinPer100,
                carbsPer100 = nutrition.carbsPer100,
                fatPer100 = nutrition.fatPer100,
                fiberPer100 = nutrition.fiberPer100,
                sugarPer100 = nutrition.sugarPer100,
                sodiumMgPer100 = nutrition.sodiumMgPer100,
                servingG = nutrition.servingG,
                source = nutrition.source
            )
        )
        nutrition
    }

    suspend fun nutritionForBarcode(code: String): Nutrition? = withContext(Dispatchers.IO) {
        val cached = db.foodDao().byExternalId("barcode:$code")
        if (cached != null) return@withContext cached.toNutrition()

        val url = URL("https://world.openfoodfacts.org/api/v2/product/$code.json" +
            "?fields=product_name,nutriments,serving_size")
        val json = fetchJson(url) ?: return@withContext null
        val product = json.optJSONObject("product") ?: return@withContext null
        if (product.length() == 0) return@withContext null
        val n = product.optJSONObject("nutriments") ?: return@withContext null

        fun v(key: String): Double {
            if (!n.has(key)) return 0.0
            return n.optDouble(key, 0.0)
        }

        val name = product.optString("product_name").ifBlank { "Product $code" }
        val nutrition = Nutrition(
            name = name,
            kcalPer100 = v("energy-kcal_100g"),
            proteinPer100 = v("proteins_100g"),
            carbsPer100 = v("carbohydrates_100g"),
            fatPer100 = v("fat_100g"),
            fiberPer100 = v("fiber_100g"),
            sugarPer100 = v("sugars_100g"),
            sodiumMgPer100 = v("sodium_100g") * 1000.0,
            servingG = 100.0,
            source = "barcode"
        )
        db.foodDao().insert(
            FoodItem(
                externalId = "barcode:$code",
                name = nutrition.name,
                kcalPer100 = nutrition.kcalPer100,
                proteinPer100 = nutrition.proteinPer100,
                carbsPer100 = nutrition.carbsPer100,
                fatPer100 = nutrition.fatPer100,
                fiberPer100 = nutrition.fiberPer100,
                sugarPer100 = nutrition.sugarPer100,
                sodiumMgPer100 = nutrition.sodiumMgPer100,
                servingG = nutrition.servingG,
                source = "barcode"
            )
        )
        nutrition
    }

    /** USDA FoodData Central, cached to Room so the second lookup needs no network. */
    private suspend fun lookupUsda(query: String): Nutrition? = withContext(Dispatchers.IO) {
        val key = query.lowercase().trim()
        db.usdaDao().get(key)?.let { row ->
            return@withContext runCatching { parseUsda(JSONObject(row.payload)) }.getOrNull()
        }
        val url = URL("https://api.nal.usda.gov/fdc/v1/foods/search?query=" +
            URLEncoder.encode(query, "UTF-8") + "&api_key=${BuildConfig.USDA_API_KEY}&pageSize=1")
        val json = fetchJson(url) ?: return@withContext null
        val foods = json.optJSONArray("foods") ?: return@withContext null
        if (foods.length() == 0) return@withContext null
        db.usdaDao().put(UsdaCache(key, foods.getJSONObject(0).toString(), System.currentTimeMillis()))
        parseUsda(foods.getJSONObject(0))
    }

    private fun parseUsda(food: JSONObject): Nutrition? {
        val nutrients = food.optJSONArray("foodNutrients") ?: return null
        val map = HashMap<String, Double>()
        for (i in 0 until nutrients.length()) {
            val n = nutrients.optJSONObject(i) ?: continue
            val name = n.optJSONObject("nutrient")?.optString("name") ?: continue
            val value = n.optDouble("value", 0.0)
            when {
                name.contains("Energy", true) -> map["kcal"] = value
                name.contains("Protein") -> map["protein"] = value
                name.contains("Carbohydrate", true) -> map["carbs"] = value
                name.contains("Total lipid", true) || name.contains("Total fat") -> map["fat"] = value
                name.contains("Fiber", true) -> map["fiber"] = value
                name.contains("Sugars", true) && !map.containsKey("sugars_total") -> {
                    map["sugars_total"] = value
                }
                name.contains("Sugars, total including NLEA", true) -> map["sugars_total"] = value
                name.contains("Sodium", true) -> map["sodium"] = value
            }
        }
        val kcal = map["kcal"] ?: return null
        if (kcal <= 0) return null
        return Nutrition(
            name = food.optString("description").ifBlank { "Unknown dish" },
            kcalPer100 = kcal,
            proteinPer100 = map["protein"] ?: 0.0,
            carbsPer100 = map["carbs"] ?: 0.0,
            fatPer100 = map["fat"] ?: 0.0,
            fiberPer100 = map["fiber"] ?: 0.0,
            sugarPer100 = map["sugars_total"] ?: 0.0,
            sodiumMgPer100 = map["sodium"] ?: 0.0,
            servingG = 100.0,
            source = "usda"
        )
    }

    private fun fetchJson(url: URL): JSONObject? = runCatching {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "CielFit/1.0 (Android; nutrition lookup)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /**
     * Heuristic fallback so a scan is never a dead end when USDA has no entry.
     * Deliberately coarse — marked as an estimate in the UI. ponytail: keyword buckets,
     * upgrade to per-dish rows if accuracy complaints show up.
     */
    private fun estimateFromLabel(label: String): Nutrition {
        val l = label.lowercase()
        val bucket = when {
            listOf("salad", "greens", "raw", "lettuce").any { it in l } ->
                Nutrition(label, 25.0, 1.5, 4.0, 0.3, fiberPer100 = 1.8)
            listOf("cake", "pie", "dessert", "pudding", "sweet", "pastry", "cookie").any { it in l } ->
                Nutrition(label, 340.0, 4.5, 45.0, 15.0, sugarPer100 = 30.0)
            listOf("fries", "fried", "crispy").any { it in l } ->
                Nutrition(label, 310.0, 3.5, 41.0, 15.0)
            listOf("soup", "broth", "stew").any { it in l } ->
                Nutrition(label, 70.0, 3.5, 9.0, 2.5)
            listOf("rice", "nasi", "grain", "pilaf").any { it in l } ->
                Nutrition(label, 130.0, 2.7, 28.0, 0.3)
            listOf("noodle", "pasta", "spaghetti", "mie").any { it in l } ->
                Nutrition(label, 158.0, 5.8, 31.0, 0.9)
            listOf("bread", "roti", "toast", "bun", "tortilla", "naan", "pita").any { it in l } ->
                Nutrition(label, 265.0, 9.0, 49.0, 3.2)
            listOf("chicken", "turkey", "poultry").any { it in l } ->
                Nutrition(label, 165.0, 31.0, 0.0, 3.6)
            listOf("steak", "beef", "lamb", "pork").any { it in l } ->
                Nutrition(label, 250.0, 26.0, 0.0, 15.0)
            listOf("fish", "salmon", "tuna", "seafood", "prawn", "shrimp", "squid").any { it in l } ->
                Nutrition(label, 140.0, 24.0, 0.0, 4.0)
            listOf("egg",).any { it in l } -> Nutrition(label, 155.0, 13.0, 1.1, 11.0)
            listOf("milk", "cheese", "yogurt", "yoghurt", "dairy").any { it in l } ->
                Nutrition(label, 240.0, 8.0, 12.0, 17.0)
            listOf("coffee", "tea", "latte", "espresso", "americano").any { it in l } ->
                Nutrition(label, 5.0, 0.3, 0.8, 0.1)
            listOf("juice", "soda", "cola", "drink", "smoothie", "lemonade").any { it in l } ->
                Nutrition(label, 45.0, 0.2, 11.0, 0.0, sugarPer100 = 10.5)
            listOf("fruit", "apple", "banana", "mango", "orange", "berry", "melon").any { it in l } ->
                Nutrition(label, 55.0, 0.7, 14.0, 0.3, fiberPer100 = 2.4)
            listOf("nut", "seed", "almond", "peanut", "cashew").any { it in l } ->
                Nutrition(label, 560.0, 20.0, 20.0, 50.0, fiberPer100 = 10.0)
            listOf("satay", "rendang", "curry", "sauce", "gravy").any { it in l } ->
                Nutrition(label, 190.0, 12.0, 8.0, 13.0, sodiumMgPer100 = 420.0)
            else -> Nutrition(label, 180.0, 8.0, 20.0, 7.0)
        }
        return bucket.copy(source = "estimate")
    }

    private fun FoodItem.toNutrition() = Nutrition(
        name = name, kcalPer100 = kcalPer100, proteinPer100 = proteinPer100,
        carbsPer100 = carbsPer100, fatPer100 = fatPer100, fiberPer100 = fiberPer100,
        sugarPer100 = sugarPer100, sodiumMgPer100 = sodiumMgPer100,
        servingG = servingG, source = source
    )
}