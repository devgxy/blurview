package com.android.example.rsmigration

import android.graphics.Bitmap

class ToolkitImageProcessor : ImageProcessor {
    override val name: String = "Toolkit"
    private lateinit var mOutputImages: Array<Bitmap>
    private lateinit var outPutImage: Bitmap
    override fun configureInputAndOutput(inputImage: Bitmap, numberOfOutputImages: Int) {
        mOutputImages = Array(numberOfOutputImages) {
            Bitmap.createBitmap(inputImage)
        }
        outPutImage = Bitmap.createBitmap(inputImage)
    }

    override fun rotateHue(radian: Float, outputIndex: Int): Bitmap {
        return mOutputImages[outputIndex]
    }

    override fun blur(radius: Float, outputIndex: Int): Bitmap {
//        return Toolkit.blur(mOutputImages[outputIndex], radius, null, outPutImage)
       return mOutputImages[0]
    }

    override fun cleanup() {
    }

}