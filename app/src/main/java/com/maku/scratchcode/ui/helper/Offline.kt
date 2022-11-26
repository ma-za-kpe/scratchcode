package com.maku.scratchcode.ui.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.MutableState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

fun runTextRecognition(bitmap: Bitmap, shouldShowProcessing: MutableState<Boolean>) {
    val recognizer: com.google.mlkit.vision.text.TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val img = InputImage.fromBitmap(bitmap, 0)
    Log.d("TAG", "ScratchApp runTextRecognition: $img")
    recognizer.process(img)
        .addOnSuccessListener { texts ->
            processTextRecognitionResult(texts)
            shouldShowProcessing.value = false
        }
        .addOnFailureListener { e -> // Task failed with an exception
            e.printStackTrace()
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
        Log.d("TAG", "ScratchApp processTextRecognitionResult:blocks.indices $i")
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

