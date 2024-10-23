/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.rsmigration

import android.content.Context
import android.graphics.Bitmap
import androidx.renderscript.*
import kotlin.math.*

class RenderScriptImageProcessor(context: Context, useIntrinsic: Boolean) : ImageProcessor {
    override val name = "RenderScript " + if (useIntrinsic) "Intrinsics" else "Scripts"

    // RenderScript 脚本
    private val mRS: RenderScript = RenderScript.create(context)
    private val mIntrinsicColorMatrix = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS))
    private val mIntrinsicBlur = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS))
    private val mScriptColorMatrix = ScriptC_colormatrix(mRS)
    private val mScriptBlur = ScriptC_blur(mRS)
    private val mUseIntrinsic = useIntrinsic

    // 输入图像
    private lateinit var mInAllocation: Allocation

    // 用于两次高斯模糊脚本的中间缓冲区
    private lateinit var mTempAllocations: Array<Allocation>

    // 输出图像
    private lateinit var mOutputImages: Array<Bitmap>
    private lateinit var mOutAllocations: Array<Allocation>

    override fun configureInputAndOutput(inputImage: Bitmap, numberOfOutputImages: Int) {
        if (numberOfOutputImages <= 0) {
            throw RuntimeException("Invalid number of output images: $numberOfOutputImages")
        }

        // 输入 Allocation
        mInAllocation = Allocation.createFromBitmap(mRS, inputImage)

        // 创建用于中间结果的 Allocation 数组
        val tempType = Type.createXY(mRS, Element.F32_4(mRS), inputImage.width, inputImage.height)
        mTempAllocations = Array(2) {
            Allocation.createTyped(mRS, tempType, Allocation.USAGE_SCRIPT)
        }

        // 输出图像和 Allocations
        mOutputImages = Array(numberOfOutputImages) {
            Bitmap.createBitmap(inputImage.width, inputImage.height, inputImage.config)
        }
        mOutAllocations = Array(numberOfOutputImages) { i ->
            Allocation.createFromBitmap(mRS, mOutputImages[i])
        }

        // 更新模糊脚本中的尺寸变量
        mScriptBlur._gWidth = inputImage.width
        mScriptBlur._gHeight = inputImage.height
    }

    override fun rotateHue(radian: Float, outputIndex: Int): Bitmap {
        // 设置色相旋转矩阵
        val cos = cos(radian.toDouble())
        val sin = sin(radian.toDouble())
        val mat = Matrix3f()
        mat[0, 0] = (.299 + .701 * cos + .168 * sin).toFloat()
        mat[1, 0] = (.587 - .587 * cos + .330 * sin).toFloat()
        mat[2, 0] = (.114 - .114 * cos - .497 * sin).toFloat()
        mat[0, 1] = (.299 - .299 * cos - .328 * sin).toFloat()
        mat[1, 1] = (.587 + .413 * cos + .035 * sin).toFloat()
        mat[2, 1] = (.114 - .114 * cos + .292 * sin).toFloat()
        mat[0, 2] = (.299 - .300 * cos + 1.25 * sin).toFloat()
        mat[1, 2] = (.587 - .588 * cos - 1.05 * sin).toFloat()
        mat[2, 2] = (.114 + .886 * cos - .203 * sin).toFloat()

        // 调用滤镜内核
        if (mUseIntrinsic) {
            mIntrinsicColorMatrix.setColorMatrix(mat)
            mIntrinsicColorMatrix.forEach(mInAllocation, mOutAllocations[outputIndex])
        } else {
            mScriptColorMatrix.invoke_setMatrix(mat)
            mScriptColorMatrix.forEach_root(mInAllocation, mOutAllocations[outputIndex])
        }

        // 复制到 Bitmap，这将导致同步而不是完全复制
        mOutAllocations[outputIndex].copyTo(mOutputImages[outputIndex])
        return mOutputImages[outputIndex]
    }

    override fun blur(radius: Float, outputIndex: Int): Bitmap {
        if (radius < 1.0f || radius > 25.0f) {
            throw RuntimeException("Invalid radius $radius, must be within [1.0, 25.0]")
        }
        if (mUseIntrinsic) {
            // 设置模糊半径
            mIntrinsicBlur.setRadius(radius)

            // 调用滤镜内核
            mIntrinsicBlur.setInput(mInAllocation)
            mIntrinsicBlur.forEach(mOutAllocations[outputIndex])
        } else {
            // 计算高斯核
            val sigma = 0.4f * radius + 0.6f
            val coeff1 = 1.0f / (sqrt(2 * Math.PI) * sigma).toFloat()
            val coeff2 = -1.0f / (2 * sigma * sigma)
            val iRadius = ceil(radius).toInt()
            val kernel = FloatArray(51) { i ->
                if (i > (iRadius * 2 + 1)) {
                    0.0f
                } else {
                    val r = (i - iRadius).toFloat()
                    coeff1 * (Math.E.toFloat().pow(coeff2 * r * r))
                }
            }
            val normalizeFactor = 1.0f / kernel.sum()
            kernel.forEachIndexed { i, v -> kernel[i] = v * normalizeFactor }

            // 应用两次模糊算法
            mScriptBlur._gRadius = iRadius
            mScriptBlur._gKernel = kernel
            mScriptBlur._gScratch1 = mTempAllocations[0]
            mScriptBlur._gScratch2 = mTempAllocations[1]
            mScriptBlur.forEach_copyIn(mInAllocation, mTempAllocations[0])
            mScriptBlur.forEach_horizontal(mTempAllocations[1])
            mScriptBlur.forEach_vertical(mOutAllocations[outputIndex])
        }

        // 复制到 Bitmap，这将导致同步而不是完全复制
        mOutAllocations[outputIndex].copyTo(mOutputImages[outputIndex])
        return mOutputImages[outputIndex]
    }

    override fun cleanup() {
        // 清理资源
        mInAllocation.destroy()
        mTempAllocations.forEach { it.destroy() }
        mOutAllocations.forEach { it.destroy() }
        mScriptBlur.destroy()
        mScriptColorMatrix.destroy()
        mIntrinsicBlur.destroy()
        mIntrinsicColorMatrix.destroy()
        mRS.destroy()
    }
}

