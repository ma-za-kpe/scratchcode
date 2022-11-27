package com.maku.scratchcode.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.maku.scratchcode.DESIRED_HEIGHT_CROP_PERCENT
import com.maku.scratchcode.DESIRED_WIDTH_CROP_PERCENT

class MainViewModel(application: Application) : AndroidViewModel(application) {
    // We set desired crop percentages to avoid having the analyze the whole image from the live
    // camera feed. However, we are not guaranteed what aspect ratio we will get from the camera, so
    // we use the first frame we get back from the camera to update these crop percentages based on
    // the actual aspect ratio of images.
    val imageCropPercentages = MutableLiveData<Pair<Int, Int>>()
        .apply { value = Pair(DESIRED_HEIGHT_CROP_PERCENT, DESIRED_WIDTH_CROP_PERCENT) }
}
