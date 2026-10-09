# CielFit release rules.
#
# R8 strips what it cannot see used. Everything below is reached by reflection,
# JNI, or a generated class, so it must be kept explicitly.

# --- TensorFlow Lite ---------------------------------------------------------
# Interpreter talks to libtensorflowlite_jni.so through native methods; R8
# renaming these classes produces a native-link failure at model load, which
# reads as "app crashes on the Scan tab" with no Java stack.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**

# --- Room --------------------------------------------------------------------
# Room's generated *Impl classes and the entities it maps by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- ZXing -------------------------------------------------------------------
# MultiFormatReader resolves readers reflectively from the format hints.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- Compose / lifecycle -----------------------------------------------------
# R8 treats Compose's AndroidCompositionLocals_androidKt as unused and strips
# it. That class is what PROVIDES LocalLifecycleOwner (and LocalContext,
# LocalConfiguration, LocalView). Without it, the first
# collectAsStateWithLifecycle() call in any screen throws
# "CompositionLocal LocalLifecycleOwner not present" and the app dies on
# launch. Debug never reproduces it, because nothing is stripped there.
# Both packages are kept: lifecycle-runtime-compose 2.8 reads
# androidx.lifecycle.compose.LocalLifecycleOwner while Compose UI 1.6.8
# provides the androidx.compose.ui.platform one.
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt$* { *; }
-keep class androidx.lifecycle.compose.** { *; }
-keep class androidx.compose.runtime.CompositionLocalKt { *; }
-keep class androidx.compose.runtime.ProvidableCompositionLocal { *; }
-keep class androidx.compose.runtime.CompositionLocal { *; }

# --- Kotlin / coroutines -----------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- App model classes -------------------------------------------------------
# Room entities and the ViewModel's public state survive by name.
-keep class com.cielfit.app.data.** { *; }
-keep class com.cielfit.app.ml.Prediction { *; }

# Keep line numbers so a release crash report is still readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
