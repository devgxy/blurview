package com.devgxy.blur

import android.graphics.Bitmap
import com.google.android.renderscript.Toolkit.blur

/**
 * 使用ToolKit实现模糊功能
 */
class ToolKitBlurImpl : IBlur {
    override var radius: Float = 1f

    override fun release() {
    }

    override fun blur(input: Bitmap, output: Bitmap) {
        blur(input, radius, outputBitmap = output)
    }
}
