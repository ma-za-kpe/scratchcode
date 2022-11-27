package com.maku.scratchcode.ui.helper

import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.MutableState
import androidx.lifecycle.MutableLiveData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

private fun debugPrint(detectedObjects: List<DetectedObject>) {
    detectedObjects.forEachIndexed { index, detectedObject ->
        val box = detectedObject.boundingBox

        Log.d("TAG", "Detected object: $index")
        Log.d("TAG", " trackingId: ${detectedObject.trackingId}")
        Log.d("TAG", " boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})")
        detectedObject.labels.forEach {
            Log.d("TAG", " categories: ${it.text}")
            Log.d("TAG", " confidence: ${it.confidence}")
        }
    }
}

fun runTextRecognition(
    image: InputImage,
    shouldShowProcessing: MutableState<Boolean>,
    imageProxy: ImageProxy
) {
    val recognizer: com.google.mlkit.vision.text.TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { texts ->
//            processTextRecognitionResult(texts)
//            processTxt(texts)
//            processMLTxt(texts)
            processRaywenderlichTxt(texts)
            shouldShowProcessing.value = false
            imageProxy.close()
        }
        .addOnFailureListener { e -> // Task failed with an exception
            e.printStackTrace()
            shouldShowProcessing.value = false
            imageProxy.close()
        }
}

fun processTextRecognitionResult(texts: Text) {
    val blocks: List<Text.TextBlock> = texts.textBlocks
    if (blocks.isEmpty()) {
        // showToast("No text found")
        return
    }

    // mGraphicOverlay.clear()
    for (i in blocks.indices) {
        Log.d("TAG", "offline ScratchApp processTextRecognitionResult:blocks.indices $i")
        val lines: List<Text.Line> = blocks[i].lines
        for (j in lines.indices) {
            val elements: List<Text.Element> = lines[j].elements
            for (k in elements.indices) {
                // val textGraphic: Graphic = TextGraphic(mGraphicOverlay, elements[k])
                // mGraphicOverlay.add(textGraphic)
            }
        }
    }
}

fun processTxt(text: Text) {
    // below line is to create a list of vision blocks which
    // we will get from our firebase vision text.
    val blocks: List<Text.TextBlock> = text.textBlocks

    // checking if the size of the
    // block is not equal to zero.
    if (blocks.size == 0) {
        // if the size of blocks is zero then we are displaying
        // a toast message as no text detected.
        // Toast.makeText(this@MainActivity, "No Text ", Toast.LENGTH_LONG).show()
        return
    }
    // extracting data from each block using a for loop.
    for (block in blocks) {
        // below line is to get text
        // from each block.
        val txt: String = block.text

        // below line is to set our
        // string to our text view.
        Log.d("TAG", "offline processTxt: $txt")
        // textview.setText(txt)
    }
}

fun processMLTxt(text: Text) {
    if (text.textBlocks.size == 0) {
        // if the size of blocks is zero then we are displaying
        // a toast message as no text detected.
        // Toast.makeText(this@MainActivity, "No Text ", Toast.LENGTH_LONG).show()
        return
    }
    for (block in text.textBlocks) {
        val blockText = block.text
        val blockCornerPoints = block.cornerPoints
        val blockFrame = block.boundingBox
        for (line in block.lines) {
            val lineText = line.text
            val lineCornerPoints = line.cornerPoints
            val lineFrame = line.boundingBox
            for (element in line.elements) {
                val elementText = element.text
                val elementCornerPoints = element.cornerPoints
                val elementFrame = element.boundingBox
                Log.d("TAG", "offline processMLTxt width: ${element.boundingBox?.width()}")
                Log.d("TAG", "offline processMLTxt height: ${element.boundingBox?.height()}")
            }
        }
    }
}

fun processRaywenderlichTxt(text: Text) {
    if (text.textBlocks.size == 0) {
        // if the size of blocks is zero then we are displaying
        // a toast message as no text detected.
        // Toast.makeText(this@MainActivity, "No Text ", Toast.LENGTH_LONG).show()
        return
    }
    text.textBlocks.forEach { block ->
        block.lines.forEach { line ->
            line.elements.forEach { element ->
                 if (element.boundingBox?.width()!! >= 70 && element.boundingBox?.height() !! >= 18) {
                     Log.d("TAG", "offline processRaywenderlichTxt width: ${element.boundingBox?.width()}")
                     Log.d("TAG", "offline processRaywenderlichTxt height: ${element.boundingBox?.height()}")
                     Log.d("TAG", "offline processRaywenderlichTxt: ${element.text}")
                 }
            }
        }
    }
}

