package com.maku.scratchcode

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Lens
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.maku.scratchcode.ui.analyzer.TextAnalyzer
import com.maku.scratchcode.ui.helper.getCameraProvider
import com.maku.scratchcode.ui.helper.takePhoto
import com.maku.scratchcode.ui.screen.ScratchCodeApp
import com.maku.scratchcode.ui.theme.ScratchCodeTheme
import com.maku.scratchcode.ui.vm.MainViewModel
import org.opencv.android.OpenCVLoader
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import androidx.camera.core.Preview as Pr


typealias mlListener = (ml: ImageProxy) -> Unit

// We only need to analyze the part of the image that has text, so we set crop percentages
// to avoid analyze the entire image from the live camera feed.
const val DESIRED_WIDTH_CROP_PERCENT = 8
const val DESIRED_HEIGHT_CROP_PERCENT = 74
private const val RATIO_4_3_VALUE = 4.0 / 3.0
private const val RATIO_16_9_VALUE = 16.0 / 9.0

@androidx.camera.core.ExperimentalGetImage
class MainActivity : ComponentActivity() {

    private lateinit var overlay: SurfaceView

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private var shouldShowCamera: MutableState<Boolean> = mutableStateOf(false)
    private var shouldShowProcessing: MutableState<Boolean> = mutableStateOf(false)

    private val viewModel: MainViewModel by viewModels()

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // replace with snack bar
                Log.d("TAG", "permission granted: ")
            } else {
                Log.d("TAG", "permission not granted: ")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = layoutInflater.inflate(R.layout.overlay, null)
        overlay = root.findViewById(R.id.overlay)

        if (!OpenCVLoader.initDebug())
            Log.e("OpenCV", "Unable to load OpenCV!");
        else
            Log.d("OpenCV", "OpenCV loaded Successfully!");

        setContent {
            val bitmap = remember { mutableStateOf<Bitmap?>(null) }

            ScratchCodeTheme {
                if (shouldShowCamera.value) {
                    CameraView(
                        outputDirectory = outputDirectory,
                        executor = cameraExecutor,
                        onImageCaptured = ::handleImageCapture,
                        onError = { Log.e("Scratch", "View error:", it) },
                        shouldShowProcessing,
                        overlay,
                        viewModel.imageCropPercentages,
                        bitmap
                    )
                } else {
                    ScratchCodeApp(shouldShowCamera, shouldShowProcessing, bitmap)
                }
            }
        }
        requestCameraPermission()
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i("Scratch", "Permission previously granted")
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> Log.i("Scratch", "Show camera permissions dialog")

            else -> requestPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun handleImageCapture(uri: Uri) {
        Log.i("Scratch", "Image captured: $uri")
        shouldShowCamera.value = false
        shouldShowProcessing.value = true
        val bitmap: Bitmap =
            MediaStore.Images.Media.getBitmap(this.contentResolver, Uri.parse(uri.toString()));
        // runTextRecognition(bitmap, shouldShowProcessing)
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@androidx.camera.core.ExperimentalGetImage
@Composable
fun CameraView(
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    shouldShowProcessing: MutableState<Boolean>,
    overlay: SurfaceView,
    imageCropPercentages: MutableLiveData<Pair<Int, Int>>,
    bitmap: MutableState<Bitmap?>
) {

    val lensFacing = CameraSelector.LENS_FACING_BACK
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val preview = Pr.Builder().build()
    val previewView = remember { PreviewView(context) }
    // Get screen metrics used to setup camera for full screen resolution
    // val metrics = DisplayMetrics().also { previewView.display.getRealMetrics(it) }
    // Log.d("TAG", "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
    // val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
    // Log.d("TAG", "Preview aspect ratio: $screenAspectRatio")
    // val rotation = previewView.display.rotation

    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()
    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            // We request aspect ratio but no resolution
            // .setTargetAspectRatio(screenAspectRatio)
            // .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    executor,
                    TextAnalyzer(shouldShowProcessing, imageCropPercentages, bitmap)
                )
            }
    }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalyzer
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)
        // previewView.overlay
    }

    ImagePreview(
        previewView,
        overlay,
        imageCapture,
        outputDirectory,
        executor,
        onImageCaptured,
        onError,
        shouldShowProcessing
    )

}

fun aspectRatio(width: Int, height: Int): Int {
    val previewRatio = ln(max(width, height).toDouble() / min(width, height))
    if (abs(previewRatio - ln(RATIO_4_3_VALUE))
        <= abs(previewRatio - ln(RATIO_16_9_VALUE))
    ) {
        return AspectRatio.RATIO_4_3
    }
    return AspectRatio.RATIO_16_9
}

@Composable
fun ImagePreview(
    previewView: PreviewView,
    overlay: SurfaceView,
    imageCapture: ImageCapture,
    outputDirectory: File,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
    shouldShowProcessing: MutableState<Boolean>
) {
    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
        val (preview, box, button) = createRefs()
        AndroidView(
            { previewView },
            modifier = Modifier
                .constrainAs(preview) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(parent.bottom)
                }
                .fillMaxSize())

        // widget.SurfaceView
        AndroidView(
            factory = { ctx ->
                overlay.apply {
                    setZOrderOnTop(true)
                    holder.setFormat(PixelFormat.TRANSPARENT)
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {}

                        override fun surfaceCreated(holder: SurfaceHolder) {
                            holder?.let {
                                drawOverlay(
                                    it,
                                    DESIRED_HEIGHT_CROP_PERCENT,
                                    DESIRED_WIDTH_CROP_PERCENT
                                )
                            }
                        }
                    })
                }
            },
            Modifier
                .constrainAs(box) {
                    top.linkTo(preview.top)
                    start.linkTo(preview.start)
                    end.linkTo(preview.end)
                    bottom.linkTo(preview.bottom)
                }, update = {
                // Update TextView with the current state value
                // it.text = "You have clicked the buttons: " + state.value.toString() + " times"
            })

        IconButton(
            modifier = Modifier
                .constrainAs(button) {
                    start.linkTo(box.start)
                    end.linkTo(box.end)
                    bottom.linkTo(box.bottom, 32.dp)
                }
                .padding(bottom = 20.dp),
            onClick = {
                Log.i("Scratch", "ON CLICK")
                takePhoto(
                    filenameFormat = "yyyy-MM-dd-HH-mm-ss-SSS",
                    imageCapture = imageCapture,
                    outputDirectory = outputDirectory,
                    executor = executor,
                    onImageCaptured = onImageCaptured,
                    onError = onError,
                    shouldShowProcessing
                )
            },
            content = {
                Icon(
                    imageVector = Icons.Sharp.Lens,
                    contentDescription = "Take picture",
                    tint = Color.White,
                    modifier = Modifier
                        .size(100.dp)
                        .padding(1.dp)
                        .border(1.dp, Color.White, CircleShape)
                )
            }
        )
    }
}

@androidx.camera.core.ExperimentalGetImage
private class ScratchImageAnalyzer(private val listener: mlListener) : ImageAnalysis.Analyzer {
    override fun analyze(imageProxy: ImageProxy) {
        listener(imageProxy)
    }
}

private fun drawOverlay(
    holder: SurfaceHolder,
    heightCropPercent: Int,
    widthCropPercent: Int
) {
    val canvas = holder.lockCanvas()
    val bgPaint = Paint().apply {
        alpha = 140
    }
    canvas.drawPaint(bgPaint)
    val rectPaint = Paint()
    rectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    rectPaint.style = Paint.Style.FILL
    rectPaint.color = android.graphics.Color.WHITE
    val outlinePaint = Paint()
    outlinePaint.style = Paint.Style.STROKE
    outlinePaint.color = android.graphics.Color.WHITE
    outlinePaint.strokeWidth = 4f
    val surfaceWidth = holder.surfaceFrame.width()
    val surfaceHeight = holder.surfaceFrame.height()

    val cornerRadius = 25f
    // Set rect centered in frame
    val rectTop = surfaceHeight * heightCropPercent / 2 / 100f
    val rectLeft = surfaceWidth * widthCropPercent / 2 / 100f
    val rectRight = surfaceWidth * (1 - widthCropPercent / 2 / 100f)
    val rectBottom = surfaceHeight * (1 - heightCropPercent / 2 / 100f)
    val rect = RectF(rectLeft, rectTop, rectRight, rectBottom)
    canvas.drawRoundRect(
        rect, cornerRadius, cornerRadius, rectPaint
    )
    canvas.drawRoundRect(
        rect, cornerRadius, cornerRadius, outlinePaint
    )
    val textPaint = Paint()
    textPaint.color = android.graphics.Color.WHITE
    textPaint.textSize = 50F

    // val overlayText = getString(R.string.overlay_help)
    val textBounds = Rect()
    textPaint.getTextBounds("Center text in box", 0, "Center text in box".length, textBounds)
    val textX = (surfaceWidth - textBounds.width()) / 2f
    val textY = rectBottom + textBounds.height() + 15f // put text below rect and 15f padding
    canvas.drawText("Center text in box", textX, textY, textPaint)
    holder.unlockCanvasAndPost(canvas)
}

@SuppressLint("UnrememberedMutableState")
@Preview("MainActivity")
@Composable
fun ScratchCodeAppPreview() {
    val shouldShowCamera: MutableState<Boolean> = mutableStateOf(false)
    val shouldShowProcessing: MutableState<Boolean> = mutableStateOf(false)
    val bitmap = remember { mutableStateOf<Bitmap?>(null) }

    ScratchCodeApp(shouldShowCamera, shouldShowProcessing, bitmap)
}
