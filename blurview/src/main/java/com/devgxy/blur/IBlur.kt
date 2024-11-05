package com.devgxy.blur;

import android.graphics.Bitmap;

public interface IBlur {

    void release();

    void blur(Bitmap input, Bitmap output);

}
