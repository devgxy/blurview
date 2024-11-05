package com.devgxy.blur

import android.graphics.Bitmap

/**
 * 模糊处理抽象类
 */
interface IBlur {
    /**
     *  模糊半径
     */
    var radius: Float

    /**
     * 释放资源
     */
    fun release()

    /**
     * 模糊处理
     * @param input 需要模糊的Bitmap
     * @param output 模糊后的数据填充的Bitmap
     */
    fun blur(input: Bitmap, output: Bitmap)
}
