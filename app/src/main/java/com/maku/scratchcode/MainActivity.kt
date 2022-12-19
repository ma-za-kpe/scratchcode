package com.maku.scratchcode

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.Rect
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
import androidx.compose.foundation.layout.height
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
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.core.CvType.CV_8UC3
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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
    private var imgBitmap: MutableState<Bitmap?> = mutableStateOf(null)

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
            Log.e("OpenCV", "Unable to load OpenCV!")
        else
            Log.d("OpenCV", "OpenCV loaded Successfully!")
        // Turn off the decor fitting system windows, which allows us to handle insets,
        // including IME animations
        // WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            ScratchCodeTheme {
                if (shouldShowCamera.value) {
                    CameraView(
                        outputDirectory = outputDirectory,
                        executor = cameraExecutor,
                        onImageCaptured = ::handleImageCapture,
                        onError = { Log.e("Scratch", "View error:", it) },
                        shouldShowProcessing,
                        overlay,
                        viewModel.imageCropPercentages
                    )
                } else {
                    ScratchCodeApp(shouldShowCamera, shouldShowProcessing, imgBitmap)
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
        val img: Bitmap =
            MediaStore.Images.Media.getBitmap(this.contentResolver, Uri.parse(uri.toString()))
        // runTextRecognition(bitmap, shouldShowProcessing)
        cleanImage(img, shouldShowProcessing, imgBitmap)
    }

    private fun cleanImage(
        img: Bitmap,
        shouldShowProcessing: MutableState<Boolean>,
        imgBitmap: MutableState<Bitmap?>
    ) {
        imgBitmap.value = img
        findRoi(img, imgBitmap)
//        val original = toMat(img)
        // gray
//        val gray8 = Mat(toMat(img).size(), CvType.CV_8UC1)
//        Imgproc.cvtColor(toMat(img), gray8, Imgproc.COLOR_RGB2GRAY)
//        imgBitmap.value = toBitmap(gray8)

        // threshold
//        val threshImageMat = Mat()
//        Imgproc.threshold(gray8, threshImageMat, 0.0, 255.0, Imgproc.THRESH_OTSU)
//        imgBitmap.value = toBitmap(threshImageMat)

        // structural elements
//        val structuralElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(18.0, 18.0))

        // Applying dilation on the threshold image
//        Imgproc.dilate(
//            threshImageMat, structuralElement, Imgproc.getStructuringElement(
//                Imgproc.MORPH_RECT, Size(
//                    2.0, 2.0
//                )
//            )
//        )

        //find the contours
//        val contours: List<MatOfPoint> = ArrayList()
//        val hierarchy = Mat(threshImageMat.height(), threshImageMat.width(), CvType.CV_8UC1)
//
//        Imgproc.findContours(
//            threshImageMat,
//            contours,
//            hierarchy,
//            Imgproc.RETR_LIST,
//            Imgproc.CHAIN_APPROX_SIMPLE
//        )

        // new MAT to draw contours on
//        val newMat = Mat()
//        newMat.create(threshImageMat.rows(), threshImageMat.cols(), CvType.CV_8UC3)
//        val r = Random()

        //Drawing the Contours
//        val color = Scalar(0.0, 0.0, 255.0)
//        Imgproc.drawContours(
//            original, contours, -1, color, 2, Imgproc.LINE_8,
//            hierarchy, 2, Point()
//        )

//        for (i in contours.indices) {
//            Imgproc.drawContours(
//                newMat, contours, i, Scalar(
//                    r.nextInt(255).toDouble(),
//                    r.nextInt(255).toDouble(), r.nextInt(255).toDouble()
//                ), -1
//            )
//        }

//        for (contourIdx in contours.indices) {
//            Imgproc.drawContours(
//                original, contours, contourIdx, Scalar(0.0, 0.0, 255.0), -1
//            )
//        }
//
//        val bmpContour = Bitmap.createBitmap(original.width(), original.height(), Bitmap.Config.ARGB_8888)
//        Utils.matToBitmap(threshImageMat, bmpContour)
//        imgBitmap.value = bmpContour

    }

    private fun findRoi(sourceBitmap: Bitmap, bit: MutableState<Bitmap?>) {
        // source
        val sourceMat = Mat(sourceBitmap.width, sourceBitmap.height, CV_8UC3)
        Utils.bitmapToMat(sourceBitmap, sourceMat)
        bit.value = toBitmap(sourceMat)

        // gray
        val grayMat = Mat(sourceBitmap.width, sourceBitmap.height, CV_8UC3)
        Imgproc.cvtColor(sourceMat, grayMat, Imgproc.COLOR_BGR2GRAY)
        bit.value = toBitmap(grayMat)

//        // structural elements
//        val rectStructuralElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(10.0, 5.0))
//
//        // Morphological operations
//        val morphologyExMat: Mat = grayMat.clone()
//        Imgproc.morphologyEx(grayMat, morphologyExMat, Imgproc.MORPH_BLACKHAT, rectStructuralElement)
//        bit.value = toBitmap(morphologyExMat)
//
//        // structural elements
//        val sqrStructuralElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
//
//        // Morphological operations
//        val morphologyExClose: Mat = morphologyExMat.clone()
//        Imgproc.morphologyEx(morphologyExMat, morphologyExClose, Imgproc.MORPH_CLOSE, sqrStructuralElement)
//        bit.value = toBitmap(morphologyExClose)


        // (OPTION 1) bilateral filter : Reducing the noise in the greyscale image
//        val bilateralMat: Mat = grayMat.clone()
//        Imgproc.bilateralFilter(grayMat, bilateralMat, 5, 10.0, 10.0)
//        bit.value = toBitmap(bilateralMat)

         // (OPTION 2) GaussianBlur filter : Reducing the noise in the greyscale image
        val gaussianMat: Mat = grayMat.clone()
        Imgproc.GaussianBlur(grayMat, gaussianMat, Size(5.0, 5.0), 0.0, 0.0)
        bit.value = toBitmap(gaussianMat)
//
//        // canny
//        val cannyMat: Mat = grayMat.clone()
//        Imgproc.Canny(gaussianMat, cannyMat, 50.0, 100.0);
//        bit.value = toBitmap(cannyMat)

        // threshold
        val thresholdMat: Mat = gaussianMat.clone()
        Imgproc.threshold(gaussianMat, thresholdMat, 0.0, 255.0, Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU)
        bit.value = toBitmap(thresholdMat)

       // find contours
        val contours: List<MatOfPoint> = ArrayList()
        val hierarchey = Mat()
        Imgproc.findContours(
            thresholdMat, contours, hierarchey, Imgproc.RETR_TREE,
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        //Drawing the Contours
        val color = Scalar(0.0, 0.0, 255.0)
        Imgproc.drawContours(
            sourceMat, contours, -1, color, 2, Imgproc.LINE_8,
            hierarchey, 2, Point()
        )

        val bitmap = Bitmap.createBitmap(sourceMat.cols(), sourceMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(sourceMat, bitmap)
        bit.value = bitmap
    }

    fun toMat(croppedBitmap: Bitmap): Mat {
        val mat = Mat()
        val bmp32: Bitmap = croppedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        Utils.bitmapToMat(bmp32, mat)
        return mat
    }

    fun toBitmap(mat: Mat): Bitmap? {
        var bmp: Bitmap? = null
        val tmp = Mat(mat.height(), mat.width(), CvType.CV_8U, Scalar(4.0))
        try {
            //Imgproc.cvtColor(seedsImage, tmp, Imgproc.COLOR_RGB2BGRA);
            Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_GRAY2RGBA, 4)
            bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(tmp, bmp)
        } catch (e: CvException) {
            Log.d("Exception", e.message!!)
        }
        return bmp
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
) {

    val lensFacing = CameraSelector.LENS_FACING_BACK
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val preview = Pr.Builder().build()
    val previewView = remember { PreviewView(context) }

    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()
    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    executor,
                    TextAnalyzer(shouldShowProcessing, imageCropPercentages)
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
//    val systemUiController = rememberSystemUiController()
//    SideEffect {
//        // set transparent color so that our image is visible
//        // under the status bar
//        systemUiController.setStatusBarColor(
//            color = Color.Transparent
//        )
//    }
    ConstraintLayout(modifier = Modifier.fillMaxSize()) {
        val (preview, box, button) = createRefs()
        AndroidView(
            { previewView },
            modifier = Modifier
                .constrainAs(preview) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(button.top, 4.dp)
                }

        )

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
                            holder.let {
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
                .height(420.dp)
                .constrainAs(box) {
                    top.linkTo(preview.top)
                    start.linkTo(preview.start)
                    end.linkTo(preview.end)
                    bottom.linkTo(preview.bottom)
//                    width = Dimension.fillToConstraints
//                    height = Dimension.matchParent
                }, update = {
                // Update TextView with the current state value
                // it.text = "You have clicked the buttons: " + state.value.toString() + " times"
            })

        IconButton(
            modifier = Modifier
                .constrainAs(button) {
                    start.linkTo(preview.start)
                    end.linkTo(preview.end)
                    bottom.linkTo(parent.bottom, 8.dp)
                }
                .padding(bottom = 16.dp),
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
                    tint = Color.Black,
                    modifier = Modifier
                        .size(100.dp)
                        .padding(1.dp)
                        .border(1.dp, Color.Black, CircleShape)
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
