package com.ljj.LiquidCamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val DeepNight = Color(0xFF080B12)
private val GlassStroke = Color.White.copy(alpha = .25f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 18)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { LiquidCameraApp() }
        }
    }
}

@Composable
private fun LiquidCameraApp() {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    Box(Modifier.fillMaxSize().background(DeepNight), contentAlignment = Alignment.Center) {
        if (granted) {
            CameraScreen()
        } else {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(54.dp))
                Text("开启相机权限", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("LiquidCamera 需要相机权限来提供实时取景和拍照功能。", color = Color.White.copy(alpha = .7f))
                Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) {
                    Text("允许使用相机")
                }
            }
        }
    }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var captureUseCase by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flash by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("准备就绪") }
    var capturing by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView) {
        val future = ProcessCameraProvider.getInstance(context)
        val bindCamera = Runnable {
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val stillCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, stillCapture
                )
                captureUseCase = stillCapture
                zoom = 1f
                status = "准备就绪"
            }.onFailure {
                status = "相机启动失败"
            }
        }
        future.addListener(bindCamera, mainExecutor)
        onDispose {
            runCatching { future.get().unbindAll() }
            camera = null
            captureUseCase = null
        }
    }

    LaunchedEffect(camera, zoom) {
        camera?.cameraControl?.setZoomRatio(zoom.coerceAtLeast(1f))
    }
    LaunchedEffect(camera, exposure) {
        camera?.let { active ->
            val range = active.cameraInfo.exposureState.exposureCompensationRange
            active.cameraControl.setExposureCompensationIndex(exposure.roundToInt().coerceIn(range.lower, range.upper))
        }
    }
    DisposableEffect(previewView, camera) {
        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                camera?.let { active ->
                    val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                    val focus = FocusMeteringAction.Builder(
                        point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                    active.cameraControl.startFocusAndMetering(focus)
                }
                view.performClick()
            }
            true
        }
        onDispose { previewView.setOnTouchListener(null) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = .48f),
                    .22f to Color.Transparent,
                    .68f to Color.Transparent,
                    1f to Color.Black.copy(alpha = .76f)
                )
            )
        )
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassPanel {
                    Column(Modifier.padding(horizontal = 17.dp, vertical = 10.dp)) {
                        Text("LIQUID", color = Color.White, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                        Text("CAMERA", color = Color.White, fontSize = 17.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                GlassPanel {
                    IconButton(onClick = {
                        flash = !flash
                        camera?.cameraControl?.enableTorch(flash)
                    }) {
                        Icon(if (flash) Icons.Default.FlashOn else Icons.Default.FlashOff, "闪光灯", tint = Color.White)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassPanel {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("曝光补偿", color = Color.White, fontSize = 12.sp)
                            Spacer(Modifier.width(12.dp))
                            Text("%+.0f".format(exposure), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(value = exposure, onValueChange = { exposure = it }, valueRange = -3f..3f, modifier = Modifier.width(220.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassPanel {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("变焦  %.1fx".format(zoom), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        val maxZoom = (camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 4f).coerceAtLeast(1.1f)
                        Slider(value = zoom.coerceIn(1f, maxZoom), onValueChange = { zoom = it }, valueRange = 1f..maxZoom, modifier = Modifier.width(220.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.width(76.dp)) {
                        Text(status, color = Color.White.copy(alpha = .78f), fontSize = 10.sp)
                        Text("PHOTO", color = Color.White, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        Modifier.size(78.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = .18f))
                            .border(BorderStroke(2.dp, Color.White.copy(alpha = .9f)), CircleShape)
                            .padding(7.dp).clip(CircleShape).background(Color.White)
                    ) {
                        IconButton(
                            onClick = {
                                val stillCapture = captureUseCase ?: return@IconButton
                                if (capturing) return@IconButton
                                capturing = true
                                status = "正在拍摄…"
                                savePhoto(context, stillCapture, {
                                    status = "照片已保存"
                                    capturing = false
                                }, {
                                    status = "拍摄失败"
                                    capturing = false
                                })
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (capturing) CircularProgressIndicator(color = DeepNight, modifier = Modifier.size(25.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.PhotoCamera, "拍照", tint = DeepNight, modifier = Modifier.size(30.dp))
                        }
                    }
                    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.End) {
                        Text("1.0.0", color = Color.White.copy(alpha = .78f), fontSize = 10.sp)
                        Text("LIQUID GLASS", color = Color.White, fontSize = 9.sp, letterSpacing = .7.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = .20f), Color.White.copy(alpha = .07f))))
            .border(1.dp, GlassStroke, RoundedCornerShape(26.dp))
    ) { content() }
}

private fun savePhoto(
    context: android.content.Context,
    capture: ImageCapture,
    onSaved: () -> Unit,
    onFailure: () -> Unit
) {
    val filename = "LiquidCamera_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LiquidCamera")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val uri = context.contentResolver.insert(collection, values)
    if (uri == null) {
        onFailure()
        return
    }
    val output = ImageCapture.OutputFileOptions.Builder(context.contentResolver, uri, values).build()
    capture.takePicture(output, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(result: ImageCapture.OutputFileResults) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, ready, null, null)
            }
            onSaved()
        }
        override fun onError(exception: ImageCaptureException) {
            context.contentResolver.delete(uri, null, null)
            onFailure()
        }
    })
}
