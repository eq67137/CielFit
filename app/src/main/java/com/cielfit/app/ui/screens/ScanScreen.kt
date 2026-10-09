package com.cielfit.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cielfit.app.data.FitViewModel
import com.cielfit.app.ui.theme.Outline
import com.cielfit.app.ui.theme.SuccessGreen
import java.util.concurrent.Executors

private enum class ScanMode(val label: String) {
    PHOTO("Photo"), BARCODE("Barcode"), SEARCH("Search")
}

private val SLOTS = listOf("breakfast", "lunch", "dinner", "snack")
private val PORTIONS = listOf(0.5, 1.0, 1.5, 2.0)

@Composable
fun ScanScreen(vm: FitViewModel) {
    var mode by remember { mutableStateOf(ScanMode.PHOTO) }
    val predictions by vm.predictions.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Scan", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ScanMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = {
                            mode = m
                            if (m != ScanMode.SEARCH) vm.clearCandidates()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, ScanMode.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = Color.Black
                        )
                    ) { Text(m.label) }
                }
            }
        }

        when (mode) {
            ScanMode.PHOTO -> item { PhotoCapture(vm) }
            ScanMode.BARCODE -> item { BarcodeCapture(vm) }
            ScanMode.SEARCH -> item { SearchBox(vm) }
        }

        if (predictions.isNotEmpty()) {
            item {
                Text(
                    if (mode == ScanMode.PHOTO) "Best matches" else "Results",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(predictions, key = { it.label }) { cand -> CandidateCard(vm, cand) }
        }
    }
}

// ---------------- photo ----------------

@Composable
private fun PhotoCapture(vm: FitViewModel) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) launcher.launch(Manifest.permission.CAMERA)
    }

    // The shutter is a real button on purpose: the model identifies the food from a
    // photo YOU framed. Auto-grabbing on open would classify whatever was in frame
    // before the camera was even pointed at the plate.
    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                .border(1.dp, Outline, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!hasCamera) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera unavailable", fontSize = 14.sp)
                    Text(
                        "Grant camera permission to photograph food",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant permission")
                    }
                }
            } else {
                CameraPreview(imageCapture)
            }
        }

        if (hasCamera) {
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    captureStill(
                        imageCapture, context, executor,
                        onOk = { bmp ->
                            busy = false
                            vm.classify(bmp)
                        },
                        onErr = { busy = false }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.CameraAlt, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan food")
                }
            }
        }

        Text(
            "Point at the food, then tap Scan food",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Bind preview only - no ImageAnalysis. A live analyzer allocates a Bitmap per frame
 * (~30/s) and blew the heap; taking a still on demand needs no per-frame work at all.
 */
@Composable
private fun CameraPreview(imageCapture: ImageCapture) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = runCatching { providerFuture.get() }.getOrNull()
                    ?: return@addListener
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture
                    )
                } catch (e: Exception) {
                    // Never swallow silently: an unbound camera looks like a frozen
                    // black box with no error anywhere.
                    Log.e("CielFit", "camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }
}

/** Take one still and hand back a Bitmap for the classifier. */
private fun captureStill(
    imageCapture: ImageCapture,
    context: android.content.Context,
    executor: java.util.concurrent.Executor,
    onOk: (android.graphics.Bitmap) -> Unit,
    onErr: () -> Unit
) {
    val file = java.io.File(context.cacheDir, "cielfit_scan.jpg")
    val opts = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        opts, executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(res: ImageCapture.OutputFileResults) {
                val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                if (bmp == null) {
                    Log.e("CielFit", "captured file would not decode: ${file.absolutePath}")
                    onErr()
                } else {
                    onOk(bmp)
                }
            }

            override fun onError(e: ImageCaptureException) {
                Log.e("CielFit", "capture failed", e)
                onErr()
            }
        }
    )
}

// ---------------- barcode ----------------

@Composable
private fun BarcodeCapture(vm: FitViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) launcher.launch(Manifest.permission.CAMERA)
    }

    val barcodeReader = remember { com.cielfit.app.ml.BarcodeReader() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanned by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                .border(1.dp, Outline, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!hasCamera) {
                Text("Camera permission needed for barcode scanning",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            // get() throws if the camera provider failed to
                            // initialise. The photo path guards this; the barcode
                            // path did not, so a failed init crashed the app
                            // instead of showing the reason.
                            val provider = runCatching { providerFuture.get() }.getOrNull()
                            if (provider == null) {
                                Log.e("CielFit", "camera provider unavailable for barcode scan")
                                return@addListener
                            }
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                // Close in a finally: an exception here leaks the
                                // proxy and CameraX stalls the stream permanently.
                                // NOTE: no early return inside the try - `return@setAnalyzer`
                                // would skip the finally and leak the proxy.
                                try {
                                    if (!scanned) {
                                        val code = barcodeReader.decode(proxy)
                                        if (code != null) {
                                            scanned = true
                                            vm.lookupBarcode(code)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("CielFit", "barcode decode failed", e)
                                } finally {
                                    proxy.close()
                                }
                            }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview, analysis
                                )
                            } catch (e: Exception) {
                                Log.e("CielFit", "barcode camera bind failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )
            }
        }
        Text(
            "Open Food Facts — real nutrition from the barcode",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------- search ----------------

@Composable
private fun SearchBox(vm: FitViewModel) {
    var query by remember { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = {
            query = it
            vm.searchFoods(it)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search 2023 foods") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true
    )
}

// ---------------- candidate card ----------------

@Composable
private fun CandidateCard(vm: FitViewModel, cand: com.cielfit.app.data.ScanCandidate) {
    var slot by remember { mutableIntStateOf(1) }
    var portionIndex by remember { mutableIntStateOf(1) }
    val portion = PORTIONS[portionIndex]
    val n = cand.nutrition

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(cand.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    if (cand.probability > 0f) {
                        Text(
                            "${(cand.probability * 100).toInt()}% confident",
                            fontSize = 11.sp,
                            color = SuccessGreen
                        )
                    }
                }
                if (cand.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            if (cand.probability > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { cand.probability.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
            }

            if (cand.failed) {
                Text(
                    "No nutrition found",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (n != null) {
                Spacer(Modifier.height(10.dp))
                val grams = (n.servingG * portion)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MacroChip("kcal", "${(n.kcalPer100 * grams / 100).toInt()}")
                    MacroChip("P", "${(n.proteinPer100 * grams / 100).toInt()}g")
                    MacroChip("C", "${(n.carbsPer100 * grams / 100).toInt()}g")
                    MacroChip("F", "${(n.fatPer100 * grams / 100).toInt()}g")
                }
                if (n.source == "estimate") {
                    Text(
                        "Estimated nutrition",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Portion", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PORTIONS.size) { i ->
                        FilterChip(
                            selected = portionIndex == i,
                            onClick = { portionIndex = i },
                            label = { Text("${PORTIONS[i]}x") }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Meal", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SLOTS.size) { i ->
                        FilterChip(
                            selected = slot == i,
                            onClick = { slot = i },
                            label = { Text(SLOTS[i].replaceFirstChar { c -> c.uppercase() }) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { vm.addFood(cand, grams, SLOTS[slot]) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add ${grams.toInt()}g to ${SLOTS[slot]}")
                }
            }
        }
    }
}

@Composable
private fun MacroChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
