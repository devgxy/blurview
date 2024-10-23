package com.devgxy.blur;

import android.graphics.Bitmap;

import com.google.android.renderscript.Toolkit;


public class ToolKitBlurImpl implements IBlur {
    float radius = 1f;

    @Override
    public void release() {

    }

    @Override
    public void blur(Bitmap input, Bitmap output) {
        Toolkit.INSTANCE.blur(input, radius, output);
    }
}
