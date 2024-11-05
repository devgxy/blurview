package com.devgxy.blur;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.renderscript.Toolkit;


import java.util.Arrays;

/**
 * A realtime blurring overlay (like iOS UIVisualEffectView). Just put it above
 * the view you want to blur and it doesn't have to be in the same ViewGroup
 *
 * @noinspection unused
 */
public class ShapeBlurView extends View {
    private static final String TAG = "ShapeBlurView";
    /**
     * 最大模糊半径
     */
    private static final int MAX_BLUR_RADIUS = Toolkit.MAX_BLUR_RADIUS;
    private Context mContext;

    /**
     * default 4
     */
    private float mDownSampleFactor;
    /**
     * default #000000
     */
    private int mOverlayColor;
    /**
     * 模糊半径，控制模糊程度
     * default 10dp (0 < r <= 25)
     */
    private float mBlurRadius;
    /**
     * 默认边框颜色为白色
     */
    public static final int DEFAULT_BORDER_COLOR = Color.WHITE;
    /**
     * 模糊实现类
     */
    private IBlur mBlurImpl;
    /**
     * 原始Bitmap，用于模糊处理
     */
    private Bitmap mBitmapToBlur;
    /**
     * 模糊后的Bitmap
     */
    private Bitmap mBlurredBitmap;
    /**
     * 绘制原始内容的Canvas，用于生成待模糊的Bitmap
     */
    private Canvas mBlurringCanvas;
    /**
     * 标志位，指示是否正在渲染
     */
    private boolean mIsRendering;
    /**
     * 原始Bitmap的Rect
     */
    private final Rect mRectSrc = new Rect();
    /**
     * 目标绘制区域的RectF
     */
    private final RectF mRectFDst = new RectF();
    /**
     * Activity的DecorView，用于获取屏幕内容
     */
    private View mDecorView;
    /**
     * 标志位，指示是否与DecorView的根视图不同
     * 如果视图在不同的根视图上（通常意味着在PopupWindow中），
     * 我们需要在onPreDraw()中手动调用invalidate()，否则看不到变化
     */
    private boolean mDifferentRoot;
    /**
     * 静态计数器，跟踪渲染次数
     */
    private static int RENDERING_COUNT;
    /**
     * 模糊模式，默认为矩形模式
     */
    private int blurShape = BlurShape.SHAPE_RECTANGLE;
    /**
     * 用于绘制Bitmap的Paint对象
     */
    private Paint mBitmapPaint;


    /**
     * 默认圆角半径为0
     */
    private static final float DEFAULT_RADIUS = 0f;
    /**
     * 存储四个角的圆角半径
     */
    private final float[] mCornerRadii =
        new float[] {DEFAULT_RADIUS, DEFAULT_RADIUS, DEFAULT_RADIUS, DEFAULT_RADIUS};
    /**
     * 用于绘制圆角路径的Path
     */
    private final Path cornerPath = new Path();
    /**
     * 用于存储圆角半径的数组，供绘制使用
     */
    private float[] cornerRadius;


    /**
     * 默认边框宽度为0
     */
    private static final float DEFAULT_BORDER_WIDTH = 0f;
    /**
     * 用于绘制边框的RectF
     */
    private final RectF mBorderRect = new RectF();
    /**
     * 用于绘制边框的Paint对象
     */
    private Paint mBorderPaint;
    /**
     * 边框宽度
     */
    private float mBorderW = 0;
    /**
     * 边框颜色
     */
    private ColorStateList mBorderColor = ColorStateList.valueOf(DEFAULT_BORDER_COLOR);
    /**
     * 用于Bitmap变换的Matrix
     */
    private Matrix matrix;
    /**
     * Bitmap着色器，用于绘制模糊效果
     */
    private BitmapShader shader;

    public ShapeBlurView(Context context) {
        this(context, null);
    }

    public ShapeBlurView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShapeBlurView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ShapeBlurView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                         int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    void init(Context context, AttributeSet attrs) {
        mContext = context;
        // provide your own by override getBlurImpl()
        mBlurImpl = getBlurImpl();
        TypedArray a = null;
        try {
            a = context.obtainStyledAttributes(attrs, R.styleable.ShapeBlurView);
            mBlurRadius = a.getDimension(R.styleable.ShapeBlurView_blur_radius,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10,
                    context.getResources().getDisplayMetrics()));
            mDownSampleFactor = a.getFloat(R.styleable.ShapeBlurView_blur_down_sample, 4);
            mOverlayColor = a.getColor(R.styleable.ShapeBlurView_blur_overlay_color, 0);

            float cornerRadiusOverride =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_corner_radius, -1);
            mCornerRadii[BlurCorner.TOP_LEFT] =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_corner_radius_top_left, -1);
            mCornerRadii[BlurCorner.TOP_RIGHT] =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_corner_radius_top_right, -1);
            mCornerRadii[BlurCorner.BOTTOM_RIGHT] =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_corner_radius_bottom_right,
                    -1);
            mCornerRadii[BlurCorner.BOTTOM_LEFT] =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_corner_radius_bottom_left,
                    -1);
            initCornerData(cornerRadiusOverride);
            blurShape = a.getInt(R.styleable.ShapeBlurView_blur_mode, BlurShape.SHAPE_RECTANGLE);

            mBorderW = a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_border_width, -1);
            if (mBorderW < 0) {
                mBorderW = DEFAULT_BORDER_WIDTH;
            }
            mBorderColor = a.getColorStateList(R.styleable.ShapeBlurView_blur_border_color);
            if (mBorderColor == null) {
                mBorderColor = ColorStateList.valueOf(DEFAULT_BORDER_COLOR);
            }
        } catch (Exception e) {
            Log.e(TAG, "ShapeBlurView", e);
        } finally {
            if (a != null) {
                a.recycle();
            }
        }
        //初始化绘制模糊区域的Paint
        mBitmapPaint = new Paint();
        mBitmapPaint.setAntiAlias(true);

        //初始化绘制边框区域的画笔Paint
        mBorderPaint = new Paint();
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setAntiAlias(true);
        mBorderPaint.setColor(mBorderColor.getColorForState(getState(), DEFAULT_BORDER_COLOR));
        mBorderPaint.setStrokeWidth(mBorderW);
    }

    /**
     * 初始化矩形区域四个角的圆角半径
     *
     * @param cornerRadiusOverride 圆角半径
     */
    private void initCornerData(float cornerRadiusOverride) {
        boolean any = false;
        // 遍历所有角的半径，若小于0则设为0，并判断是否有任意角被设置
        for (int i = 0, len = mCornerRadii.length; i < len; i++) {
            if (mCornerRadii[i] < 0) {
                mCornerRadii[i] = 0f;
            } else {
                any = true;
            }
        }
        if (!any) {
            if (cornerRadiusOverride < 0) {
                cornerRadiusOverride = DEFAULT_RADIUS;
            }
            // 将所有角的半径设置为cornerRadiusOverride
            Arrays.fill(mCornerRadii, cornerRadiusOverride);
        }
        initCornerRadius();
    }

    /**
     * 初始化用于绘制的角半径数组
     */
    private void initCornerRadius() {
        if (cornerRadius == null) {
            // 创建新的角半径数组，顺序为左上、右上、右下、左下，每个角有x和y两个半径值
            cornerRadius =
                new float[] {mCornerRadii[BlurCorner.TOP_LEFT], mCornerRadii[BlurCorner.TOP_LEFT],
                    mCornerRadii[BlurCorner.TOP_RIGHT], mCornerRadii[BlurCorner.TOP_RIGHT],
                    mCornerRadii[BlurCorner.BOTTOM_RIGHT], mCornerRadii[BlurCorner.BOTTOM_RIGHT],
                    mCornerRadii[BlurCorner.BOTTOM_LEFT], mCornerRadii[BlurCorner.BOTTOM_LEFT]};
        } else {
            //更新已有的角半径数组
            cornerRadius[0] = mCornerRadii[BlurCorner.TOP_LEFT];
            cornerRadius[1] = mCornerRadii[BlurCorner.TOP_LEFT];
            cornerRadius[2] = mCornerRadii[BlurCorner.TOP_RIGHT];
            cornerRadius[3] = mCornerRadii[BlurCorner.TOP_RIGHT];
            cornerRadius[4] = mCornerRadii[BlurCorner.BOTTOM_RIGHT];
            cornerRadius[5] = mCornerRadii[BlurCorner.BOTTOM_RIGHT];
            cornerRadius[6] = mCornerRadii[BlurCorner.BOTTOM_LEFT];
            cornerRadius[7] = mCornerRadii[BlurCorner.BOTTOM_LEFT];
        }
    }

    /**
     * 获取模糊实现类，可以通过重写此方法提供自定义实现
     */
    protected IBlur getBlurImpl() {
        return new ToolKitBlurImpl();
    }

    /**
     * 获取最大的圆角半径
     *
     * @return 最大的圆角半径
     */
    public float getMaxCornerRadius() {
        float maxRadius = 0;
        for (float r : mCornerRadii) {
            maxRadius = Math.max(r, maxRadius);
        }
        return maxRadius;
    }


    /**
     * 获取边框宽度
     *
     * @return 边框宽度
     */
    public float getBorderWidth() {
        return mBorderW;
    }

    /**
     * 获取边框颜色
     *
     * @return 边框颜色
     */
    @NonNull
    public ColorStateList getBorderColor() {
        return mBorderColor;
    }

    /**
     * 获取模糊形状
     *
     * @return 模糊形状 {@link BlurShape}
     */
    @BlurShape
    public int getBlurShape() {
        return this.blurShape;
    }

    /**
     * 释放图片资源
     */
    private void releaseBitmap() {
        if (mBitmapToBlur != null) {
            mBitmapToBlur.recycle();
            mBitmapToBlur = null;
        }
        if (mBlurredBitmap != null) {
            mBlurredBitmap.recycle();
            mBlurredBitmap = null;
        }
        if (matrix != null) {
            matrix = null;
        }
        if (shader != null) {
            shader = null;
        }
        mContext = null;
    }

    /**
     * 释放资源
     */
    protected void release() {
        releaseBitmap();
        mBlurImpl.release();
    }

    /**
     * 准备模糊处理
     *
     * @return 是否准备成功
     */
    protected boolean prepare() {
        //模糊半径0 不需要模糊处理
        if (mBlurRadius == 0) {
            release();
            return false;
        }
        float downSampleFactor = mDownSampleFactor;
        float radius = mBlurRadius / downSampleFactor;
        // 模糊半径不能超过MAX_BLUR_RADIUS，调整降采样因子
        if (radius > MAX_BLUR_RADIUS) {
            downSampleFactor = downSampleFactor * radius / MAX_BLUR_RADIUS;
        }
        final int width = getWidth();
        final int height = getHeight();
        int scaledWidth = Math.max(1, (int) (width / downSampleFactor));
        int scaledHeight = Math.max(1, (int) (height / downSampleFactor));
        if (mBlurringCanvas == null || mBlurredBitmap == null ||
            mBlurredBitmap.getWidth() != scaledWidth ||
            mBlurredBitmap.getHeight() != scaledHeight) {
            boolean normal = false;
            try {
                if (mBitmapToBlur != null && mBitmapToBlur.getWidth() > scaledWidth &&
                    mBitmapToBlur.getHeight() > scaledHeight) {
                    mBitmapToBlur.reconfigure(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                } else {
                    mBitmapToBlur =
                        Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                }
                mBlurringCanvas = new Canvas(mBitmapToBlur);
                if (mBlurredBitmap != null && mBlurredBitmap.getWidth() > scaledWidth &&
                    mBlurredBitmap.getHeight() > scaledHeight) {
                    mBlurredBitmap.reconfigure(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                } else {
                    mBlurredBitmap =
                        Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888);
                }
                normal = true;
            } catch (Exception e) {
                Log.e(TAG, "prepare", e);
                return false;
            } finally {
                if (!normal) {
                    release();
                }
            }
        }
        return true;
    }

    /**
     * 执行模糊处理
     *
     * @param bitmapToBlur  需要模糊的Bitmap
     * @param blurredBitmap 模糊后的Bitmap
     */
    protected void blur(Bitmap bitmapToBlur, Bitmap blurredBitmap) {
        shader = new BitmapShader(blurredBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        mBlurImpl.blur(bitmapToBlur, blurredBitmap);
    }

    /**
     * 视图树的预绘制监听器
     */
    private final ViewTreeObserver.OnPreDrawListener preDrawListener =
        new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                Log.d(TAG, "onPreDraw start： " + this.hashCode());
                final int[] locations = new int[2];
                Bitmap oldBmp = mBlurredBitmap;
                View decor = mDecorView;
                if (decor != null && isShown() && prepare()) {
                    boolean redrawBitmap = mBlurredBitmap != oldBmp;
                    decor.getLocationOnScreen(locations);
                    int x = -locations[0];
                    int y = -locations[1];
                    getLocationOnScreen(locations);
                    x += locations[0];
                    y += locations[1];
                    // just erase transparent
                    mBitmapToBlur.eraseColor(0);
                    int rc = mBlurringCanvas.save();
                    mIsRendering = true;
                    RENDERING_COUNT++;
                    try {
                        //画布缩放到mBitmapToBlur 大小
                        mBlurringCanvas.scale(1.f * mBitmapToBlur.getWidth() / getWidth(),
                            1.f * mBitmapToBlur.getHeight() / getHeight());
                        //画布平移到view位置
                        mBlurringCanvas.translate(-x, -y);

                        //绘制decorView和decorView背景 到画布上
                        if (decor.getBackground() != null) {
                            decor.getBackground().draw(mBlurringCanvas);
                        }
                        decor.draw(mBlurringCanvas);
                    } catch (StopException ignored) {
                    } finally {
                        mIsRendering = false;
                        RENDERING_COUNT--;
                        mBlurringCanvas.restoreToCount(rc);
                    }

                    //模糊mBitmapToBlur，模糊后的结果输出到mBlurredBitmap
                    blur(mBitmapToBlur, mBlurredBitmap);

                    //重新绘制
                    if (redrawBitmap || mDifferentRoot) {
                        invalidate();
                    }
                }
                Log.d(TAG, "onPreDraw end：" + this.hashCode());
                return true;
            }
        };

    protected View getActivityDecorView() {
        Context ctx = getContext();
        for (int i = 0; i < 4 && !(ctx instanceof Activity) && ctx instanceof ContextWrapper; i++) {
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        if (ctx instanceof Activity) {
            return ((Activity) ctx).getWindow().getDecorView();
        } else {
            return null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDecorView = getActivityDecorView();
        if (mDecorView != null) {
            mDecorView.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            mDifferentRoot = mDecorView.getRootView() != getRootView();
            if (mDifferentRoot) {
                mDecorView.postInvalidate();
            }
        } else {
            mDifferentRoot = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mDecorView != null) {
            mDecorView.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        }
        release();
        super.onDetachedFromWindow();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mIsRendering) {
            // Quit here, don't draw views above me
            throw STOP_EXCEPTION;
        } else if (RENDERING_COUNT > 0) {
            // Doesn't support blurView overlap on another blurView
            Log.w(TAG, "draw, Doesn't support blurView overlap on another blurView");
        } else {
            super.draw(canvas);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        try {
            Log.i(TAG, "onDraw, width: " + getWidth() + ", height: " + getHeight());
            canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            drawBlurredBitmap(canvas, mBlurredBitmap, mOverlayColor);
            canvas.restore();
        } catch (Exception e) {
            Log.e(TAG, "onDraw", e);
        }
    }

    /**
     * Custom draw the blurred bitmap and color to define your own shape
     */
    protected void drawBlurredBitmap(Canvas canvas, Bitmap blurBitmap, int overlayColor) {
        if (blurBitmap != null) {
            if (blurShape == BlurShape.SHAPE_CIRCLE) {
                drawCircleRectBitmap(canvas, blurBitmap, overlayColor);
            } else if (blurShape == BlurShape.SHAPE_OVAL) {
                drawOvalRectBitmap(canvas, blurBitmap, overlayColor);
            } else {
                drawRoundRectBitmap(canvas, blurBitmap, overlayColor);
            }
        }
    }

    /**
     * 默认或者画矩形可带圆角
     */
    private void drawRoundRectBitmap(Canvas canvas, Bitmap blurBitmap, int overlayColor) {
        //Path.Direction.CW：clockwise ，沿顺时针方向绘制,Path.Direction.CCW：counter-clockwise ，沿逆时针方向绘制
        //先重置，再设置
        cornerPath.reset();
        float borderHalfW = mBorderW / 2f;
        cornerPath.addRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, Path.Direction.CW);
        cornerPath.close();
        canvas.clipPath(cornerPath);

        //绘制模糊区域
        mRectFDst.set(mBorderW, mBorderW, getWidth() - mBorderW, getHeight() - mBorderW);
        mRectSrc.set(0, 0, blurBitmap.getWidth(), blurBitmap.getHeight());
        canvas.drawBitmap(blurBitmap, mRectSrc, mRectFDst, null);

        //绘制覆盖颜色值
        mBitmapPaint.setColor(overlayColor);
        canvas.drawRect(mRectFDst, mBitmapPaint);

        //绘制边框
        if (mBorderW > 0) {
            //先重置，再设置
            cornerPath.reset();
            cornerPath.addRoundRect(borderHalfW, borderHalfW, getWidth() - borderHalfW,
                getHeight() - borderHalfW, cornerRadius, Path.Direction.CW);
            mBorderPaint.setStrokeWidth(mBorderW);
            canvas.drawPath(cornerPath, mBorderPaint);
        }
    }

    /**
     * 画椭圆，如果宽高一样则为圆形
     */
    private void drawOvalRectBitmap(Canvas canvas, Bitmap blurBitmap, int overlayColor) {
        mBitmapPaint.reset();
        mBitmapPaint.setAntiAlias(true);
        if (shader == null) {
            shader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        if (matrix == null) {
            matrix = new Matrix();
        } else {
            matrix.reset();
        }
        matrix.postScale((getWidth() - mBorderW) / (float) blurBitmap.getWidth(),
            (getHeight() - mBorderW) / (float) blurBitmap.getHeight());
        shader.setLocalMatrix(matrix);
        mBitmapPaint.setShader(shader);
        canvas.drawOval(mBorderW, mBorderW, getWidth() - mBorderW, getHeight() - mBorderW,
            mBitmapPaint);

        mBitmapPaint.reset();
        mBitmapPaint.setAntiAlias(true);
        mBitmapPaint.setColor(overlayColor);
        float borderHalfW = mBorderW / 2;
        canvas.drawOval(mBorderW, mBorderW, getWidth() - mBorderW, getHeight() - mBorderW,
            mBitmapPaint);
        if (mBorderW > 0) {
            mBorderRect.set(0, 0, getWidth(), getHeight());
            mBorderRect.inset(mBorderW / 2, mBorderW / 2);
            canvas.drawOval(mBorderRect, mBorderPaint);
        }
    }

    /**
     * 画圆形，以宽高最小的为半径
     */
    private void drawCircleRectBitmap(Canvas canvas, Bitmap blurBitmap, int overlayColor) {
        // 初始化目标矩形，设置为视图的尺寸
        Log.i(TAG, "drawCircleRectBitmap start");
        mRectFDst.set(0, 0, getWidth(), getHeight());
        // 初始化源矩形，设置为位图的尺寸
        mRectSrc.set(0, 0, blurBitmap.getWidth(), blurBitmap.getHeight());

        mBitmapPaint.reset();
        mBitmapPaint.setAntiAlias(true);
        if (shader == null) {
            shader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        if (matrix == null) {
            matrix = new Matrix();
        } else {
            matrix.reset();
        }
        matrix.postScale(mRectFDst.width() / mRectSrc.width(),
            mRectFDst.height() / mRectSrc.height());
        shader.setLocalMatrix(matrix);
        mBitmapPaint.setShader(shader);
        //前面Scale，故判断以哪一个来取中心点和半径
        //圆形 相关
        float cRadius, cx, cy;
        float borderHalfW = mBorderW / 2f;
        if (getWidth() >= blurBitmap.getWidth()) {
            //圆心坐标位置取大的矩形的宽高一半
            cx = getWidth() / 2f;
            cy = getHeight() / 2f;
            //取宽高小的为半径
            cRadius = Math.min(getWidth(), getHeight()) / 2f;
            mBorderRect.set(borderHalfW, borderHalfW, getWidth() - borderHalfW,
                getHeight() - borderHalfW);
        } else {
            cx = blurBitmap.getWidth() / 2f;
            cy = blurBitmap.getHeight() / 2f;
            cRadius = Math.min(blurBitmap.getWidth(), blurBitmap.getHeight()) / 2f;
            mBorderRect.set(borderHalfW, borderHalfW, blurBitmap.getWidth() - borderHalfW,
                blurBitmap.getHeight() - borderHalfW);
        }
        //绘制模糊图片
        canvas.drawCircle(cx, cy, cRadius, mBitmapPaint);
        mBitmapPaint.reset();
        mBitmapPaint.setAntiAlias(true);
        mBitmapPaint.setColor(overlayColor);
        //绘制纯色覆盖
        canvas.drawCircle(cx, cy, cRadius, mBitmapPaint);

        //使用宽高相等的椭圆为圆形来画边框
        if (mBorderW > 0) {
            float width = mBorderRect.width();
            float height = mBorderRect.height();
            float minSide = Math.min(width, height);
            float difX = (width - minSide) / 2;
            float difY = (height - minSide) / 2;

            // 将矩形调整为正方形并居中
            mBorderRect.left += difX;
            mBorderRect.top += difY;
            mBorderRect.right = mBorderRect.left + minSide;
            mBorderRect.bottom = mBorderRect.top + minSide;

            // 内缩边框宽度的一半
            mBorderRect.inset(mBorderW / 2, mBorderW / 2);

            // 绘制圆形边框
            mBorderPaint.setStrokeWidth(mBorderW);
            canvas.drawOval(mBorderRect, mBorderPaint);
        }
        Log.i(TAG, "drawCircleRectBitmap end");
    }

    public @NonNull int[] getState() {
        return StateSet.WILD_CARD;
    }

    private static class StopException extends RuntimeException {
    }

    private static final StopException STOP_EXCEPTION = new StopException();

    /**
     * 传入构造器，避免传统的设置一个参数调用一次invalidate()重新绘制
     */
    public void refreshView(Builder builder) {
        boolean isInvalidate = false;
        if (builder == null) {
            return;
        }
        if (builder.blurMode != -1 && this.blurShape != builder.blurMode) {
            this.blurShape = builder.blurMode;
            isInvalidate = true;
        }
        if (builder.mBorderColor != null && !mBorderColor.equals(builder.mBorderColor)) {
            this.mBorderColor = builder.mBorderColor;
            mBorderPaint.setColor(mBorderColor.getColorForState(getState(), DEFAULT_BORDER_COLOR));
            if (mBorderW > 0) {
                isInvalidate = true;
            }
        }
        if (builder.mBorderWidth > 0) {
            mBorderW = builder.mBorderWidth;
            mBorderPaint.setStrokeWidth(mBorderW);
            isInvalidate = true;
        }
        if (mCornerRadii[BlurCorner.TOP_LEFT] != builder.mCornerRadii[BlurCorner.TOP_LEFT] ||
            mCornerRadii[BlurCorner.TOP_RIGHT] != builder.mCornerRadii[BlurCorner.TOP_RIGHT] ||
            mCornerRadii[BlurCorner.BOTTOM_RIGHT] !=
                builder.mCornerRadii[BlurCorner.BOTTOM_RIGHT] ||
            mCornerRadii[BlurCorner.BOTTOM_LEFT] != builder.mCornerRadii[BlurCorner.BOTTOM_LEFT]) {
            mCornerRadii[BlurCorner.TOP_LEFT] = builder.mCornerRadii[BlurCorner.TOP_LEFT];
            mCornerRadii[BlurCorner.TOP_RIGHT] = builder.mCornerRadii[BlurCorner.TOP_RIGHT];
            mCornerRadii[BlurCorner.BOTTOM_LEFT] = builder.mCornerRadii[BlurCorner.BOTTOM_LEFT];
            mCornerRadii[BlurCorner.BOTTOM_RIGHT] = builder.mCornerRadii[BlurCorner.BOTTOM_RIGHT];
            isInvalidate = true;
            initCornerRadius();
        }
        if (builder.mOverlayColor != -1 && mOverlayColor != builder.mOverlayColor) {
            mOverlayColor = builder.mOverlayColor;
            isInvalidate = true;
        }

        if (builder.mBlurRadius > 0 && mBlurRadius != builder.mBlurRadius) {
            mBlurRadius = builder.mBlurRadius;
            isInvalidate = true;
        }
        if (builder.mDownSampleFactor > 0 && mDownSampleFactor != builder.mDownSampleFactor) {
            mDownSampleFactor = builder.mDownSampleFactor;
            isInvalidate = true;
            releaseBitmap();
        }
        if (isInvalidate) {
            invalidate();
        }
    }

    /**
     * @noinspection unused
     */
    public static class Builder {
        // default 4
        private float mDownSampleFactor = -1;
        // default #aaffffff
        private int mOverlayColor = -1;
        // default 10dp (0 < r <= 25)
        private float mBlurRadius = -1;
        private float mBorderWidth = -1;
        private ColorStateList mBorderColor = null;
        private int blurMode = -1;
        private final float[] mCornerRadii = new float[] {0f, 0f, 0f, 0f};
        private final Context mContext;

        private Builder(Context context) {
            mContext = context.getApplicationContext();
        }

        /**
         * 模糊半径
         *
         * @param radius 0~25
         */
        public Builder setBlurRadius(@FloatRange(from = 0, to = 25) float radius) {
            mBlurRadius = radius;
            return this;
        }

        /**
         * 采样率
         *
         * @param factor 采样率
         */
        public Builder setDownSampleFactor(float factor) {
            if (factor <= 0) {
                throw new IllegalArgumentException("DownSample factor must be greater than 0.");
            }
            mDownSampleFactor = factor;
            return this;
        }

        /**
         * 蒙层颜色
         *
         * @param color 蒙层颜色
         */
        public Builder setOverlayColor(int color) {
            mOverlayColor = color;
            return this;
        }

        /**
         * Set the corner radius of a specific corner in px.
         * 设置圆角 圆形、椭圆无效
         *
         * @param corner 枚举类型 对应4个角
         * @param radius 角半径幅度
         */
        public Builder setCornerRadius(@BlurCorner int corner, float radius) {
            mCornerRadii[corner] = radius;
            return this;
        }

        /**
         * Set all the corner radii from a dimension resource id.
         * 设置圆角 圆形、椭圆无效
         *
         * @param resId dimension resource id of radii.
         */
        public Builder setCornerRadiusDimen(@DimenRes int resId) {
            float radius = mContext.getResources().getDimension(resId);
            return setCornerRadius(radius, radius, radius, radius);
        }

        /**
         * Set the corner radius of a specific corner in px.
         * 设置圆角 圆形、椭圆无效
         *
         * @param radius 4个角同值
         */
        public Builder setCornerRadius(float radius) {
            return setCornerRadius(radius, radius, radius, radius);
        }

        /**
         * Set the corner radius of a specific corner in px.
         * 设置圆角 圆形、椭圆无效
         */
        public Builder setCornerRadius(float topLeft, float topRight, float bottomLeft,
                                       float bottomRight) {
            mCornerRadii[BlurCorner.TOP_LEFT] = topLeft;
            mCornerRadii[BlurCorner.TOP_RIGHT] = topRight;
            mCornerRadii[BlurCorner.BOTTOM_LEFT] = bottomLeft;
            mCornerRadii[BlurCorner.BOTTOM_RIGHT] = bottomRight;
            return this;
        }

        /**
         * 设置边框的宽度
         *
         * @param resId 资源ID
         */
        public Builder setBorderWidth(@DimenRes int resId) {
            return setBorderWidth(mContext.getResources().getDimension(resId));
        }

        /**
         * 设置边框的宽度
         *
         * @param width 转px值
         */
        public Builder setBorderWidth(float width) {
            mBorderWidth = width;
            return this;
        }

        /**
         * 设置边框颜色
         *
         * @param color R.color.xxxx
         */
        public Builder setBorderColor(@ColorRes int color) {
            return setBorderColor(ColorStateList.valueOf(ContextCompat.getColor(mContext, color)));
        }

        public Builder setBorderColor(ColorStateList colors) {
            mBorderColor = (colors != null) ? colors : ColorStateList.valueOf(DEFAULT_BORDER_COLOR);
            return this;
        }

        /**
         * 设置高斯模糊的类型
         *
         * @param blurMode BlurMode枚举值，支持圆、方形、椭圆（宽高相等椭圆为圆）
         */
        public Builder setBlurMode(@BlurShape int blurMode) {
            this.blurMode = blurMode;
            return this;
        }

    }

    /**
     * 建造者模式，避免设置一个参数调用一次重新绘制
     */
    public static Builder build(Context context) {
        return new Builder(context);
    }

}
