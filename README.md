# CielFit

Android fitness and diet tracker — calorie and macro targets, on-device food photo scanning, barcode lookup, and 264 Filipino dishes from the FNRI Philippine Food Composition Tables.

## Download

Grab `CielFit.apk` from the [v1.0 release](https://github.com/eq67137/CielFit/releases/tag/v1.0), then on your phone: allow installs from unknown sources, open the file, install.

Requires Android 8.0+ (minSdk 26). The camera is optional — the app installs and the rest works on devices without one.

## Features

| Area | Detail |
|---|---|
| Calorie target | Mifflin–St Jeor BMR → TDEE by activity level → cut / maintain / bulk |
| Macros | Animated gradient rings and daily bars for protein, carbs, fat |
| Food photo scan | On-device TFLite model, 2023 dishes, **fully offline** — nothing leaves the phone |
| Barcode scan | Real nutrition per product from Open Food Facts |
| Filipino dishes | 264 dishes sourced from FNRI (adobo, sinigang, kare-kare, halo-halo, lechon, balut, pandesal…) |
| Tracking | Water, weight, body measurements, BMI, 30-day trends |

## How it works

- **No account, no backend.** Everything is stored locally in a Room database.
- **The food model is bundled** (~21 MB) and runs on-device via TensorFlow Lite, so scanning works with no network and no API key.
- **Nutrition lookup order:** local cache → bundled FNRI Filipino foods → USDA FoodData Central → keyword estimate. Anything that resolves is cached, so a repeat lookup needs no network.
- FNRI rows outrank the USDA call because USDA FoodData Central contains no authentic Filipino dishes.

## Known limits

- The photo model **identifies the dish only** — it does not estimate portion size. You pick 0.5× / 1× / 1.5× / 2× yourself.
- The classifier is trained mostly on North American foods, so accuracy is best on common dishes.
- Nutrition for an unmatched regional dish falls back to a keyword heuristic and is labelled *Estimated* in the app.
- Release builds are signed with a debug key, so this is for sideloading, not the Play Store.

## Build from source

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Credits

- Food classifier: `google/aiy/vision/classifier/food_V1`
- Philippine nutrition: [FNRI Philippine Food Composition Tables](https://i.fnri.dost.gov.ph/fct/library/search_item)
- Product nutrition: [Open Food Facts](https://world.openfoodfacts.org)