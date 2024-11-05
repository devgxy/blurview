package com.devgxy.blur

import androidx.annotation.IntDef

@Retention(AnnotationRetention.SOURCE)
@IntDef(
    BlurCorner.TOP_LEFT, BlurCorner.TOP_RIGHT, BlurCorner.BOTTOM_LEFT, BlurCorner.BOTTOM_RIGHT
)
/**
 * 定义四个角的圆角半径
 */
annotation class BlurCorner {
    companion object {
        /**
         * 左上角半径
         */
        const val TOP_LEFT: Int = 0

        /**
         * 右上角半径
         */
        const val TOP_RIGHT: Int = 1

        /**
         * 右下角半径
         */
        const val BOTTOM_RIGHT: Int = 2

        /**
         * 左下角半径
         */
        const val BOTTOM_LEFT: Int = 3
    }
}
