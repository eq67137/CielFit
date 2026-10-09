# CielFit — Android Calorie & Fitness Tracker

Design spec. 2026-10-08.

## Purpose

A native Android fitness + diet app that tracks calories, macros, water, weight and body
measurements, and identifies food three ways: on-device photo ML, barcode, and text search.
No account, no backend, no API keys to buy.

## Verified assets (probed 2026-10-08)

| Piece | Source | Status |
|---|---|---|
| Photo classifier | `google/aiy/TfLite/vision-classifier-food-v1` via Kaggle model API | Downloaded, 21,155,840 B, TFLite magic `lite` verified, 2023 classes |
| Class labels | `gstatic.com/aihub/tfhub/labelmaps/aiy_food_V1_labelmap.csv` | Downloaded, 2023 names + `__background__` |
| Barcode nutrition | Open Food Facts v2 product API | HTTP 200 keyless, per-100g nutriments |
| Dish nutrition | USDA FoodData Central `DEMO_KEY` | HTTP 200 keyless |
| JDK / SDK / Gradle | Local: `AndroidTools\jdk\jdk-17.0.20.1+1`, `AppData\Local\Android\Sdk` (android-34, build-tools 34.0.0), `AndroidTools\gradle-8.7` | Present, nothing to download |

### Model contract (probed with ai-edge-litert, not assumed)

The Kaggle download is a **gzip-wrapped tar**, not a bare tflite — `1.tflite` inside. Kaggle
labels the instance `tfLite` but the archive holds a single tar member.

Verified by allocating tensors and running a zero input:

| Property | Value |
|---|---|
| Input | `input`, `[1, 192, 192, 3]`, **uint8**, quant scale `0.0078125`, zero-point `128` |
| Output | `MobilenetV1/Predictions/Softmax`, `[1, 2024]`, uint8, scale `0.00390625`, zp `0` |
| Classes | 2024 = 2023 foods + `__background__` (labelmap row 0) |

Consequences the code must honour:
- Resize to **192x192**, feed **uint8** — no float scaling, no /255.
- Dequantize output: `prob = byte * 0.00390625`.
- Class count is 2024, labels 2024 including background; drop background from results.
- XNNPACK delegate works (logged on load).

Trained skewed toward North American food, but the label set covers Malaysian/Asian dishes
(nasi lemak, roti jala, satay, rendang, char kway teow).

## Stack

Kotlin, Jetpack Compose, Material 3, Room, MediaPipe Tasks Vision (TFLite), Retrofit-free
(Ktor-free: plain `HttpURLConnection` + `org.json`), Coil not needed (no remote images).
minSdk 26, compileSdk 34, targetSdk 34, JVM target 17.

## Architecture

```
app/src/main/java/com/cielfit/app/
  MainActivity.kt          Compose host, nav bar, theme
  ui/theme/                Color.kt, Type.kt, Theme.kt  (dark-first, gradient rings)
  ui/screens/              TodayScreen, ScanScreen, LogScreen, TrendsScreen,
                           PlanScreen, BodyScreen
  ui/components/           CalorieRing, MacroBar, WaterPicker, StatTile, TopFoodRow
  ml/FoodClassifier.kt     TFLite wrapper: load, classify(bitmap): List<Prediction>
  data/                    AppDatabase.kt, entities, DAOs, Converters
  data/FoodRepository.kt   local label DB + OFF barcode + USDA dish lookup, w/ cache
  domain/Nutrition.kt      pure Kotlin: bmr(), tdee(), macroSplit(), pct()
```

Boundaries: `domain/` has no Android imports (unit-testable in plain JVM). `ml/` is the only
code touching TFLite. Screens never call `FoodClassifier` directly — they go through
`FoodRepository`, which handles caching and fallback.

## Features

1. **Today** — gradient calorie ring (consumed / target), macro bars for protein/carbs/fat,
   water quick-add row (250/500/750 ml), remaining-kcal hero, meal list for today.
2. **Scan** — three tabs:
   - *Photo*: camera capture -> top-5 predictions with confidence bars -> dish picker ->
     portion multiplier (0.5/1/1.5/2) -> add to a meal slot.
   - *Barcode*: scan EAN -> Open Food Facts -> nutrition panel -> add.
   - *Search*: fuzzy search across the bundled 2023-label database, then USDA lookup.
3. **Log** — date-grouped entries, meal-slot grouping, edit quantity, delete.
4. **Trends** — 7/14/30-day weight line chart, calorie adherence bars, top-5 most-logged foods.
5. **Plan** — BMR via Mifflin-St Jeor, TDEE via activity multiplier, macro split by goal
   (cut / maintain / bulk), editable daily targets, profile form (sex, age, height, weight,
   activity level).
6. **Body** — weight log + chart, body measurements (waist, chest, arm, thigh), BMI.

## Data model

Room entities:
- `food_item` — cached nutrition per food id/source (id, name, kcal, protein, carbs, fat,
  fiber, sugar, sodium, source, serving_g)
- `meal_entry` — log row (id, dateMillis, slot, foodId, name, grams, kcal, p, c, f, ...)
- `water_log` — (id, dateMillis, ml)
- `body_metric` — (id, dateMillis, weight_kg, waist, chest, arm, thigh)
- `app_settings` — single-row key/value (sex, age, height, goal, activity, targets)
- `usda_cache` — (query_key, payload_json) for offline dish nutrition

## Permissions

`CAMERA`, `INTERNET`. Camera declared `required="false"` so the app still installs on
devices without a camera; barcode/photo paths degrade to a message.

## Testing

`domain/NutritionTest.kt` — assert-based self-check for bmr, tdee, macro split, and
percentage math. Pure Kotlin, no framework. Fails loudly if the nutrition math breaks.

## Out of scope for v1

Cloud vision APIs, user accounts/sync, exercise library + burned-calorie tracking,
recipe builder, barcode generation, Wear OS.