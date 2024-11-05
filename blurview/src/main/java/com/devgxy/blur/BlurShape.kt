package com.devgxy.blur

import androidx.annotation.IntDef

@Retention(AnnotationRetention.SOURCE)
@IntDef(
    BlurShape.SHAPE_RECTANGLE, BlurShape.SHAPE_CIRCLE, BlurShape.SHAPE_OVAL
)
/**
 * 模糊形状定义
 */
annotation class BlurShape {
    companion object {
        /**
         * 圆角矩形
         */
        const val SHAPE_RECTANGLE: Int = 0

        /**
         * 圆形
         */
        const val SHAPE_CIRCLE: Int = 1

        /**
         * 椭圆形
         */
        const val SHAPE_OVAL: Int = 2
    }
}
