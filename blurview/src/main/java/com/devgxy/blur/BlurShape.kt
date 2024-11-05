package com.devgxy.blur;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


@Retention(RetentionPolicy.SOURCE)
@IntDef({
    BlurShape.SHAPE_RECTANGLE, BlurShape.SHAPE_CIRCLE,
    BlurShape.SHAPE_OVAL
})
public @interface BlurShape {
    int SHAPE_RECTANGLE = 0;
    int SHAPE_CIRCLE = 1;
    int SHAPE_OVAL = 2;
}
