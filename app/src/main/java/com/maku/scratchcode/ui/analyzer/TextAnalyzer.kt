package com.maku.scratchcode.ui.analyzer

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.MutableState
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc


/**
 * Analyzes the frames passed in from the camera and returns any detected text within the requested
 * crop region.
 */
class TextAnalyzer(
    private val shouldShowProcessing: MutableState<Boolean>,
    private val imageCropPercentages: MutableLiveData<Pair<Int, Int>>,
    private val bitmap: MutableState<Bitmap?>
) : ImageAnalysis.Analyzer {

    val recognizer: com.google.mlkit.vision.text.TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // We requested a setTargetAspectRatio, but it's not guaranteed that's what the camera
        // stack is able to support, so we calculate the actual ratio from the first frame to
        // know how to appropriately crop the image we want to analyze.
        val imageHeight = mediaImage.height
        val imageWidth = mediaImage.width

        val actualAspectRatio = imageWidth / imageHeight

        val convertImageToBitmap = ImageUtils.convertYuv420888ImageToBitmap(mediaImage)
        val cropRect = Rect(0, 0, imageWidth, imageHeight)

        // If the image has a way wider aspect ratio than expected, crop less of the height so we
        // don't end up cropping too much of the image. If the image has a way taller aspect ratio
        // than expected, we don't have to make any changes to our cropping so we don't handle it
        // here.
        val currentCropPercentages = imageCropPercentages.value ?: return
        if (actualAspectRatio > 3) {
            val originalHeightCropPercentage = currentCropPercentages.first
            val originalWidthCropPercentage = currentCropPercentages.second
            imageCropPercentages.value =
                Pair(originalHeightCropPercentage / 2, originalWidthCropPercentage)
        }

        // If the image is rotated by 90 (or 270) degrees, swap height and width when calculating
        // the crop.
        val cropPercentages = imageCropPercentages.value ?: return
        val heightCropPercent = cropPercentages.first
        val widthCropPercent = cropPercentages.second
        val (widthCrop, heightCrop) = when (rotationDegrees) {
            90, 270 -> Pair(heightCropPercent / 100f, widthCropPercent / 100f)
            else -> Pair(widthCropPercent / 100f, heightCropPercent / 100f)
        }

        cropRect.inset(
            (imageWidth * widthCrop / 2).toInt(),
            (imageHeight * heightCrop / 2).toInt()
        )
        val croppedBitmap = ImageUtils.rotateAndCrop(convertImageToBitmap, rotationDegrees, cropRect)
        bitmap.value = croppedBitmap
        // pass the cropped bitmap to open cv then pass to firebase ML
//         doImagePrep(croppedBitmap, bitmap, shouldShowProcessing)
//        openCv(croppedBitmap, bitmap, shouldShowProcessing)
//        findLargestRectangle(croppedBitmap, bitmap, shouldShowProcessing)
//        recognizeTextOnDevice(InputImage.fromBitmap(croppedBitmap, 0), shouldShowProcessing).addOnCompleteListener {
//            imageProxy.close()
//        }
    }

    private fun doImagePrep(
        croppedBitmap: Bitmap,
        bitmap: MutableState<Bitmap?>,
        shouldShowProcessing: MutableState<Boolean>
    ) {
        val original = toMat(croppedBitmap)
        val gray8 = Mat(toMat(croppedBitmap).size(), CvType.CV_8UC1)
        Imgproc.cvtColor(toMat(croppedBitmap), gray8, Imgproc.COLOR_RGB2GRAY)
        bitmap.value = toBitmap(gray8)
    }

    private fun openCv(
        croppedBitmap: Bitmap,
        bitmap: MutableState<Bitmap?>,
        shouldShowProcessing: MutableState<Boolean>
    ) {
        val original = toMat(croppedBitmap)
        val gray8 = Mat(toMat(croppedBitmap).size(), CvType.CV_8UC1)
        Imgproc.cvtColor(toMat(croppedBitmap), gray8, Imgproc.COLOR_RGB2GRAY)
        bitmap.value = toBitmap(gray8)
//        val mean = Core.mean(gray8)
//        Imgproc.threshold(
//            gray8, gray8, mean.`val`[0], 255.0,
//            Imgproc.THRESH_BINARY
//        )
//        /*Imgproc.erode(gray8, gray8, new Mat(), new Point(-1, -1), 2);*/
//        /*Imgproc.erode(gray8, gray8, new Mat(), new Point(-1, -1), 2);*/
//        val contours: List<MatOfPoint> = ArrayList()
//        val hierarchy = MatOfInt4()
//        Imgproc.findContours(
//            gray8, contours, hierarchy,
//            Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE
//        )
////        Toast.makeText(
////            getApplicationContext(),
////            contours.size.toString() + " yo",
////            Toast.LENGTH_SHORT
////        ).show()
//        for (contourIdx in contours.indices) {
//            Imgproc.drawContours(
//                original,
//                contours,
//                contourIdx,
//                Scalar(0.0, 0.0, 255.0),
//                -1,
//                1,
//                hierarchy,
//                50,
//                Point(1.0, 1.0)
//            )
//        }
//        gray8.convertTo(gray8, CvType.CV_32S)
////        Imgproc.watershed(original, gray8)
////        gray8.convertTo(gray8, CvType.CV_8UC1)
//        bitmap.value = toBitmap(gray8)
    }

    private fun findLargestRectangle(
        croppedBitmap: Bitmap,
        bitmap: MutableState<Bitmap?>,
        shouldShowProcessing: MutableState<Boolean>
    ) {
        val original_image = toMat(croppedBitmap)
        val bmpOriOut = Bitmap.createBitmap(
            original_image.cols(),
            original_image.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(original_image, bmpOriOut)

        // convert the image to black and white,
        Imgproc.cvtColor(original_image, original_image, Imgproc.COLOR_BGR2RGB)
        bitmap.value = toBitmap(original_image)

//         convert the image to black and white does (8 bit),
//        Imgproc.Canny(original_image, original_image, 50.0, 50.0)

//        //apply gaussian blur to smoothen lines of dots, commenting this crashes
//        Imgproc.GaussianBlur(original_image, original_image, Size(5.0, 5.0), 5.0)
//
//        //find the contours
//        val contours: List<MatOfPoint> = ArrayList()
//        Imgproc.findContours(
//            original_image,
//            contours,
//            Mat(),
//            Imgproc.RETR_LIST,
//            Imgproc.CHAIN_APPROX_SIMPLE
//        )
//        var maxArea = -1.0
//        var maxAreaIdx = -1
//        var temp_contour = contours[0] //the largest is at the index 0 for starting point
//        val approxCurve = MatOfPoint2f()
//        var largest_contour: Mat = contours[0]
//        var largest_contours: MutableList<MatOfPoint?> = ArrayList()
//        for (idx in contours.indices) {
//            temp_contour = contours[idx]
//            val contourarea = Imgproc.contourArea(temp_contour)
//            //compare this contour to the previous largest contour found
//            if (contourarea > maxArea) {
//                //check if this contour is a square
//                val new_mat = MatOfPoint2f(*temp_contour.toArray())
//                val contourSize = temp_contour.total().toInt()
//                Imgproc.approxPolyDP(new_mat, approxCurve, contourSize * 0.05, true)
//                if (approxCurve.total() == 4L) {
//                    maxArea = contourarea
//                    maxAreaIdx = idx
//                    largest_contours.add(temp_contour)
//                    largest_contour = temp_contour
//                }
//            }
//        }
//        val temp_largest = largest_contours[largest_contours.size - 1]
//        largest_contours = ArrayList()
//        largest_contours.add(temp_largest)
//        Imgproc.cvtColor(original_image, original_image, Imgproc.COLOR_BayerBG2RGB)
//        Imgproc.drawContours(original_image, largest_contours, -1, Scalar(0, 255, 0), 1)
//
//        //create the new image here using the largest detected square
//
//        //Toast.makeText(getApplicationContext(), "Largest Contour: ", Toast.LENGTH_LONG).show();
//        val bmpOut = Bitmap.createBitmap(
//            original_image.cols(),
//            original_image.rows(),
//            Bitmap.Config.ARGB_8888
//        )
//        Utils.matToBitmap(original_image, bmpOut)
//        try {
//            bmpOut.compress(
//                CompressFormat.JPEG,
//                100,
//                FileOutputStream("/sdcard/mediaAppPhotos/bigrect.jpg")
//            )
//        } catch (e: FileNotFoundException) {
//            // TODO Auto-generated catch block
//            e.printStackTrace()
//        }
//        return original_image
//    }
//
//    private fun toBitmap(mat: Mat): Bitmap? {
//        var bmp: Bitmap? = null
//        val tmp = Mat(mat.height(), mat.width(), CvType.CV_8U, Scalar(4.0))
//        try {
//            //Imgproc.cvtColor(seedsImage, tmp, Imgproc.COLOR_RGB2BGRA);
//            Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_GRAY2RGBA, 4)
//            bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888)
//            Utils.matToBitmap(tmp, bmp)
//        } catch (e: CvException) {
//            Log.d("Exception", e.message!!)
//        }
//        return bmp
    }

    private fun toBitmap(mat: Mat): Bitmap? {
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

    private fun toMat(croppedBitmap: Bitmap): Mat {
        val mat = Mat()
        val bmp32: Bitmap = croppedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        Utils.bitmapToMat(bmp32, mat)
        // return Mat (croppedBitmap.height, croppedBitmap.width, CvType.CV_8UC1)
        return mat
    }

    private fun recognizeTextOnDevice(
        image: InputImage,
        shouldShowProcessing: MutableState<Boolean>
    ): Task<Text> {
        // Pass image to an ML Kit Vision API
        return recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Task completed successfully, so pass to ml text recongnition
                // result.value = visionText.text
                processTxt(visionText)
                shouldShowProcessing.value = false
            }
            .addOnFailureListener { exception ->
                // Task failed with an exception
                Log.e(TAG, "Text recognition error", exception)
                val message = getErrorMessage(exception)
                message?.let {
                    // Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                shouldShowProcessing.value = false
            }
    }

    private fun getErrorMessage(exception: Exception): String? {
        val mlKitException = exception as? MlKitException ?: return exception.message
        return if (mlKitException.errorCode == MlKitException.UNAVAILABLE) {
            "Waiting for text recognition model to be downloaded"
        } else exception.message
    }

    fun processTxt(text: Text) {
        if (text.textBlocks.size == 0) {
            // if the size of blocks is zero then we are displaying
            // a toast message as no text detected.
            // Toast.makeText(this@MainActivity, "No Text ", Toast.LENGTH_LONG).show()
            return
        }
        text.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEach { element ->
                    Log.d("TAG", "offline processTxt width: ${element.boundingBox?.width()}")
                    Log.d("TAG", "offline processTxt height: ${element.boundingBox?.height()}")
                    Log.d("TAG", "offline processTxt: ${element.text}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "TextAnalyzer"
    }
}
