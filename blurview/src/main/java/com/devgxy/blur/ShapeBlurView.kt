package com.devgxy.blur

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.util.StateSet
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver.OnPreDrawListener
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.FloatRange
import androidx.core.content.ContextCompat
import com.google.android.renderscript.Toolkit
import java.util.Arrays
import kotlin.math.max
import kotlin.math.min

/**
 * A realtime blurring overlay (like iOS UIVisualEffectView). Just put it above
 * the view you want to blur and it doesn't have to be in the same ViewGroup
 *
 * @noinspection unused
 */
open class ShapeBlurView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes) {
    private var mContext: Context? = null

    /**
     * default 4
     */
    private var mDownSampleFactor = 0f

    /**
     * default #000000
     */
    private var mOverlayColor = 0

    /**
     * 模糊半径，控制模糊程度
     * default 10dp (0 < r <= 25)
     */
    private var mBlurRadius = 0f

    /**
     * 模糊实现类
     */
    private lateinit var mBlurImpl: IBlur

    /**
     * 原始Bitmap，用于模糊处理
     */
    private var mBitmapToBlur: Bitmap? = null

    /**
     * 模糊后的Bitmap
     */
    private var mBlurredBitmap: Bitmap? = null

    /**
     * 绘制原始内容的Canvas，用于生成待模糊的Bitmap
     */
    private var mBlurringCanvas: Canvas? = null

    /**
     * 标志位，指示是否正在渲染
     */
    private var mIsRendering = false

    /**
     * 原始Bitmap的Rect
     */
    private val mRectSrc = Rect()

    /**
     * 目标绘制区域的RectF
     */
    private val mRectFDst = RectF()

    /**
     * Activity的DecorView，用于获取屏幕内容
     */
    private var mDecorView: View? = null

    /**
     * 标志位，指示是否与DecorView的根视图不同
     * 如果视图在不同的根视图上（通常意味着在PopupWindow中），
     * 我们需要在onPreDraw()中手动调用invalidate()，否则看不到变化
     */
    private var mDifferentRoot = false
    /**
     * 获取模糊形状
     *
     * @return 模糊形状 [BlurShape]
     */
    /**
     * 模糊模式，默认为矩形模式
     */
    @get:BlurShape
    var blurShape: Int = BlurShape.SHAPE_RECTANGLE
        private set

    /**
     * 用于绘制Bitmap的Paint对象
     */
    private var mBitmapPaint: Paint? = null


    /**
     * 存储四个角的圆角半径
     */
    private val mCornerRadii =
        floatArrayOf(DEFAULT_RADIUS, DEFAULT_RADIUS, DEFAULT_RADIUS, DEFAULT_RADIUS)

    /**
     * 用于绘制圆角路径的Path
     */
    private val cornerPath = Path()

    /**
     * 用于存储圆角半径的数组，供绘制使用
     */
    private var cornerRadius: FloatArray? = null


    /**
     * 用于绘制边框的RectF
     */
    private val mBorderRect = RectF()

    /**
     * 用于绘制边框的Paint对象
     */
    private var mBorderPaint: Paint? = null
    /**
     * 获取边框宽度
     *
     * @return 边框宽度
     */
    /**
     * 边框宽度
     */
    private var borderWidth: Float = 0f
    /**
     * 获取边框颜色
     *
     * @return 边框颜色
     */
    /**
     * 边框颜色
     */
    private var borderColor: ColorStateList = ColorStateList.valueOf(DEFAULT_BORDER_COLOR)

    /**
     * 用于Bitmap变换的Matrix
     */
    private var matrix: Matrix? = null

    /**
     * Bitmap着色器，用于绘制模糊效果
     */
    private var shader: BitmapShader? = null

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)

    private fun init(context: Context, attrs: AttributeSet?) {
        mContext = context
        // provide your own by override getBlurImpl()
        mBlurImpl = blurImpl
        var a: TypedArray? = null
        try {
            a = context.obtainStyledAttributes(attrs, R.styleable.ShapeBlurView)
            mBlurRadius = a.getDimension(
                R.styleable.ShapeBlurView_blur_radius,
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 10f,
                    context.resources.displayMetrics
                )
            )
            mBlurImpl.radius = mBlurRadius

            mDownSampleFactor = a.getFloat(R.styleable.ShapeBlurView_blur_down_sample, 4f)
            mOverlayColor = a.getColor(R.styleable.ShapeBlurView_blur_overlay_color, 0)

            val cornerRadiusOverride =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_corner_radius, -1).toFloat()
            mCornerRadii[BlurCorner.TOP_LEFT] =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_corner_radius_top_left, -1)
                    .toFloat()
            mCornerRadii[BlurCorner.TOP_RIGHT] =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_corner_radius_top_right, -1)
                    .toFloat()
            mCornerRadii[BlurCorner.BOTTOM_RIGHT] =
                a.getDimensionPixelSize(
                    R.styleable.ShapeBlurView_blur_corner_radius_bottom_right,
                    -1
                ).toFloat()
            mCornerRadii[BlurCorner.BOTTOM_LEFT] =
                a.getDimensionPixelSize(
                    R.styleable.ShapeBlurView_blur_corner_radius_bottom_left,
                    -1
                ).toFloat()
            initCornerData(cornerRadiusOverride)
            blurShape = a.getInt(R.styleable.ShapeBlurView_blur_mode, BlurShape.SHAPE_RECTANGLE)

            borderWidth =
                a.getDimensionPixelSize(R.styleable.ShapeBlurView_blur_border_width, -1).toFloat()
            if (borderWidth < 0) {
                borderWidth = DEFAULT_BORDER_WIDTH
            }
            borderColor = a.getColorStateList(R.styleable.ShapeBlurView_blur_border_color)!!
        } catch (e: Exception) {
            Log.e(TAG, "ShapeBlurView", e)
        } finally {
            a?.recycle()
        }
        //初始化绘制模糊区域的Paint
        mBitmapPaint = Paint()
        mBitmapPaint!!.isAntiAlias = true

        //初始化绘制边框区域的画笔Paint
        mBorderPaint = Paint()
        mBorderPaint!!.style = Paint.Style.STROKE
        mBorderPaint!!.isAntiAlias = true
        mBorderPaint!!.color = borderColor.getColorForState(state, DEFAULT_BORDER_COLOR)
        mBorderPaint!!.strokeWidth = borderWidth
    }

    /**
     * 初始化矩形区域四个角的圆角半径
     *
     * @param cornerRadiusOverride 圆角半径
     */
    private fun initCornerData(cornerRadiusOverride: Float) {
        var cornerRadius = cornerRadiusOverride
        var any = false
        // 遍历所有角的半径，若小于0则设为0，并判断是否有任意角被设置
        var i = 0
        val len = mCornerRadii.size
        while (i < len) {
            if (mCornerRadii[i] < 0) {
                mCornerRadii[i] = 0f
            } else {
                any = true
            }
            i++
        }
        if (!any) {
            if (cornerRadius < 0) {
                cornerRadius = DEFAULT_RADIUS
            }
            // 将所有角的半径设置为cornerRadiusOverride
            Arrays.fill(mCornerRadii, cornerRadius)
        }
        initCornerRadius()
    }

    /**
     * 初始化用于绘制的角半径数组
     */
    private fun initCornerRadius() {
        if (cornerRadius == null) {
            // 创建新的角半径数组，顺序为左上、右上、右下、左下，每个角有x和y两个半径值
            cornerRadius =
                floatArrayOf(
                    mCornerRadii[BlurCorner.TOP_LEFT], mCornerRadii[BlurCorner.TOP_LEFT],
                    mCornerRadii[BlurCorner.TOP_RIGHT], mCornerRadii[BlurCorner.TOP_RIGHT],
                    mCornerRadii[BlurCorner.BOTTOM_RIGHT], mCornerRadii[BlurCorner.BOTTOM_RIGHT],
                    mCornerRadii[BlurCorner.BOTTOM_LEFT], mCornerRadii[BlurCorner.BOTTOM_LEFT]
                )
        } else {
            //更新已有的角半径数组
            cornerRadius!![0] = mCornerRadii[BlurCorner.TOP_LEFT]
            cornerRadius!![1] = mCornerRadii[BlurCorner.TOP_LEFT]
            cornerRadius!![2] = mCornerRadii[BlurCorner.TOP_RIGHT]
            cornerRadius!![3] = mCornerRadii[BlurCorner.TOP_RIGHT]
            cornerRadius!![4] = mCornerRadii[BlurCorner.BOTTOM_RIGHT]
            cornerRadius!![5] = mCornerRadii[BlurCorner.BOTTOM_RIGHT]
            cornerRadius!![6] = mCornerRadii[BlurCorner.BOTTOM_LEFT]
            cornerRadius!![7] = mCornerRadii[BlurCorner.BOTTOM_LEFT]
        }
    }

    private val blurImpl: IBlur
        /**
         * 获取模糊实现类，可以通过重写此方法提供自定义实现
         */
        get() = ToolKitBlurImpl()

    val maxCornerRadius: Float
        /**
         * 获取最大的圆角半径
         *
         * @return 最大的圆角半径
         */
        get() {
            var maxRadius = 0f
            for (r in mCornerRadii) {
                maxRadius = max(r.toDouble(), maxRadius.toDouble()).toFloat()
            }
            return maxRadius
        }


    /**
     * 释放图片资源
     */
    private fun releaseBitmap() {
        if (mBitmapToBlur != null) {
            mBitmapToBlur!!.recycle()
            mBitmapToBlur = null
        }
        if (mBlurredBitmap != null) {
            mBlurredBitmap!!.recycle()
            mBlurredBitmap = null
        }
        if (matrix != null) {
            matrix = null
        }
        if (shader != null) {
            shader = null
        }
        mContext = null
    }

    /**
     * 释放资源
     */
    private fun release() {
        releaseBitmap()
        mBlurImpl!!.release()
    }

    /**
     * 准备模糊处理
     *
     * @return 是否准备成功
     */
    protected fun prepare(): Boolean {
        //模糊半径0 不需要模糊处理
        if (mBlurRadius == 0f) {
            release()
            return false
        }
        var downSampleFactor = mDownSampleFactor
        val radius = mBlurRadius / downSampleFactor
        // 模糊半径不能超过MAX_BLUR_RADIUS，调整降采样因子
        if (radius > MAX_BLUR_RADIUS) {
            downSampleFactor = downSampleFactor * radius / MAX_BLUR_RADIUS
        }
        val width = width
        val height = height
        val scaledWidth = max(1.0, (width / downSampleFactor).toInt().toDouble())
            .toInt()
        val scaledHeight = max(1.0, (height / downSampleFactor).toInt().toDouble())
            .toInt()
        if (mBlurringCanvas == null || mBlurredBitmap == null || mBlurredBitmap!!.width != scaledWidth || mBlurredBitmap!!.height != scaledHeight) {
            var normal = false
            try {
                if (mBitmapToBlur != null && mBitmapToBlur!!.width > scaledWidth && mBitmapToBlur!!.height > scaledHeight) {
                    mBitmapToBlur!!.reconfigure(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                } else {
                    mBitmapToBlur =
                        Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                }
                mBlurringCanvas = Canvas(mBitmapToBlur!!)
                if (mBlurredBitmap != null && mBlurredBitmap!!.width > scaledWidth && mBlurredBitmap!!.height > scaledHeight) {
                    mBlurredBitmap!!.reconfigure(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                } else {
                    mBlurredBitmap =
                        Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                }
                normal = true
            } catch (e: Exception) {
                Log.e(TAG, "prepare", e)
                return false
            } finally {
                if (!normal) {
                    release()
                }
            }
        }
        return true
    }

    /**
     * 执行模糊处理
     *
     * @param bitmapToBlur  需要模糊的Bitmap
     * @param blurredBitmap 模糊后的Bitmap
     */
    protected fun blur(bitmapToBlur: Bitmap?, blurredBitmap: Bitmap?) {
        shader = BitmapShader(blurredBitmap!!, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        if (bitmapToBlur != null) {
            mBlurImpl!!.blur(bitmapToBlur, blurredBitmap)
        }
    }

    /**
     * 视图树的预绘制监听器
     */
    private val preDrawListener: OnPreDrawListener = object : OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            Log.d(TAG, "onPreDraw start： " + this.hashCode())
            val locations = IntArray(2)
            val oldBmp = mBlurredBitmap
            val decor = mDecorView
            if (decor != null && isShown && prepare()) {
                val redrawBitmap = mBlurredBitmap != oldBmp
                decor.getLocationOnScreen(locations)
                var x = -locations[0]
                var y = -locations[1]
                getLocationOnScreen(locations)
                x += locations[0]
                y += locations[1]
                // just erase transparent
                mBitmapToBlur!!.eraseColor(0)
                val rc = mBlurringCanvas!!.save()
                mIsRendering = true
                RENDERING_COUNT++
                try {
                    //画布缩放到mBitmapToBlur 大小
                    mBlurringCanvas!!.scale(
                        1f * mBitmapToBlur!!.width / width,
                        1f * mBitmapToBlur!!.height / height
                    )
                    //画布平移到view位置
                    mBlurringCanvas!!.translate(-x.toFloat(), -y.toFloat())

                    //绘制decorView和decorView背景 到画布上
                    if (decor.background != null) {
                        decor.background.draw(mBlurringCanvas!!)
                    }
                    decor.draw(mBlurringCanvas!!)
                } catch (ignored: StopException) {
                } finally {
                    mIsRendering = false
                    RENDERING_COUNT--
                    mBlurringCanvas!!.restoreToCount(rc)
                }

                //模糊mBitmapToBlur，模糊后的结果输出到mBlurredBitmap
                blur(mBitmapToBlur, mBlurredBitmap)

                //重新绘制
                if (redrawBitmap || mDifferentRoot) {
                    invalidate()
                }
            }
            Log.d(TAG, "onPreDraw end：" + this.hashCode())
            return true
        }
    }

    protected val activityDecorView: View?
        get() {
            var ctx = context
            var i = 0
            while (i < 4 && ctx !is Activity && ctx is ContextWrapper) {
                ctx = ctx.baseContext
                i++
            }
            return if (ctx is Activity) {
                ctx.window.decorView
            } else {
                null
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mDecorView = activityDecorView
        if (mDecorView != null) {
            mDecorView!!.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            mDifferentRoot = mDecorView!!.rootView !== rootView
            if (mDifferentRoot) {
                mDecorView!!.postInvalidate()
            }
        } else {
            mDifferentRoot = false
        }
    }

    override fun onDetachedFromWindow() {
        if (mDecorView != null) {
            mDecorView!!.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        release()
        super.onDetachedFromWindow()
    }

    override fun draw(canvas: Canvas) {
        if (mIsRendering) {
            // Quit here, don't draw views above me
            throw STOP_EXCEPTION
        } else if (RENDERING_COUNT > 0) {
            // Doesn't support blurView overlap on another blurView
            Log.w(TAG, "draw, Doesn't support blurView overlap on another blurView")
        } else {
            super.draw(canvas)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            Log.i(TAG, "onDraw, width: $width, height: $height")
            canvas.save()
            canvas.clipRect(0, 0, width, height)
            drawBlurredBitmap(canvas, mBlurredBitmap, mOverlayColor)
            canvas.restore()
        } catch (e: Exception) {
            Log.e(TAG, "onDraw", e)
        }
    }

    /**
     * Custom draw the blurred bitmap and color to define your own shape
     */
    protected fun drawBlurredBitmap(canvas: Canvas, blurBitmap: Bitmap?, overlayColor: Int) {
        if (blurBitmap != null) {
            if (blurShape == BlurShape.SHAPE_CIRCLE) {
                drawCircleRectBitmap(canvas, blurBitmap, overlayColor)
            } else if (blurShape == BlurShape.SHAPE_OVAL) {
                drawOvalRectBitmap(canvas, blurBitmap, overlayColor)
            } else {
                drawRoundRectBitmap(canvas, blurBitmap, overlayColor)
            }
        }
    }

    /**
     * 默认或者画矩形可带圆角
     */
    private fun drawRoundRectBitmap(canvas: Canvas, blurBitmap: Bitmap, overlayColor: Int) {
        //Path.Direction.CW：clockwise ，沿顺时针方向绘制,Path.Direction.CCW：counter-clockwise ，沿逆时针方向绘制
        //先重置，再设置
        cornerPath.reset()
        val borderHalfW = borderWidth / 2f
        cornerPath.addRoundRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            cornerRadius!!,
            Path.Direction.CW
        )
        cornerPath.close()
        canvas.clipPath(cornerPath)

        //绘制模糊区域
        mRectFDst[borderWidth, borderWidth, width - borderWidth] = height - borderWidth
        mRectSrc[0, 0, blurBitmap.width] = blurBitmap.height
        canvas.drawBitmap(blurBitmap, mRectSrc, mRectFDst, null)

        //绘制覆盖颜色值
        mBitmapPaint!!.color = overlayColor
        canvas.drawRect(mRectFDst, mBitmapPaint!!)

        //绘制边框
        if (borderWidth > 0) {
            //先重置，再设置
            cornerPath.reset()
            cornerPath.addRoundRect(
                borderHalfW, borderHalfW, width - borderHalfW,
                height - borderHalfW, cornerRadius!!, Path.Direction.CW
            )
            mBorderPaint!!.strokeWidth = borderWidth
            canvas.drawPath(cornerPath, mBorderPaint!!)
        }
    }

    /**
     * 画椭圆，如果宽高一样则为圆形
     */
    private fun drawOvalRectBitmap(canvas: Canvas, blurBitmap: Bitmap, overlayColor: Int) {
        mBitmapPaint!!.reset()
        mBitmapPaint!!.isAntiAlias = true
        if (shader == null) {
            shader = BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        if (matrix == null) {
            matrix = Matrix()
        } else {
            matrix!!.reset()
        }
        matrix!!.postScale(
            (width - borderWidth) / blurBitmap.width.toFloat(),
            (height - borderWidth) / blurBitmap.height.toFloat()
        )
        shader!!.setLocalMatrix(matrix)
        mBitmapPaint!!.setShader(shader)
        canvas.drawOval(
            borderWidth, borderWidth, width - borderWidth, height - borderWidth,
            mBitmapPaint!!
        )

        mBitmapPaint!!.reset()
        mBitmapPaint!!.isAntiAlias = true
        mBitmapPaint!!.color = overlayColor

        canvas.drawOval(
            borderWidth, borderWidth, width - borderWidth, height - borderWidth,
            mBitmapPaint!!
        )
        if (borderWidth > 0) {
            mBorderRect[0f, 0f, width.toFloat()] = height.toFloat()
            mBorderRect.inset(borderWidth / 2, borderWidth / 2)
            canvas.drawOval(mBorderRect, mBorderPaint!!)
        }
    }

    /**
     * 画圆形，以宽高最小的为半径
     */
    private fun drawCircleRectBitmap(canvas: Canvas, blurBitmap: Bitmap, overlayColor: Int) {
        // 初始化目标矩形，设置为视图的尺寸
        Log.i(TAG, "drawCircleRectBitmap start")
        mRectFDst[0f, 0f, width.toFloat()] = height.toFloat()
        // 初始化源矩形，设置为位图的尺寸
        mRectSrc[0, 0, blurBitmap.width] = blurBitmap.height

        mBitmapPaint!!.reset()
        mBitmapPaint!!.isAntiAlias = true
        if (shader == null) {
            shader = BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        if (matrix == null) {
            matrix = Matrix()
        } else {
            matrix!!.reset()
        }
        matrix!!.postScale(
            mRectFDst.width() / mRectSrc.width(),
            mRectFDst.height() / mRectSrc.height()
        )
        shader!!.setLocalMatrix(matrix)
        mBitmapPaint!!.setShader(shader)
        //前面Scale，故判断以哪一个来取中心点和半径
        //圆形 相关
        val cRadius: Float
        val cx: Float
        val cy: Float
        val borderHalfW = borderWidth / 2f
        if (width >= blurBitmap.width) {
            //圆心坐标位置取大的矩形的宽高一半
            cx = width / 2f
            cy = height / 2f
            //取宽高小的为半径
            cRadius = (min(width.toDouble(), height.toDouble()) / 2f).toFloat()
            mBorderRect[borderHalfW, borderHalfW, width - borderHalfW] = height - borderHalfW
        } else {
            cx = blurBitmap.width / 2f
            cy = blurBitmap.height / 2f
            cRadius =
                (min(blurBitmap.width.toDouble(), blurBitmap.height.toDouble()) / 2f).toFloat()
            mBorderRect[borderHalfW, borderHalfW, blurBitmap.width - borderHalfW] =
                blurBitmap.height - borderHalfW
        }
        //绘制模糊图片
        canvas.drawCircle(cx, cy, cRadius, mBitmapPaint!!)
        mBitmapPaint!!.reset()
        mBitmapPaint!!.isAntiAlias = true
        mBitmapPaint!!.color = overlayColor
        //绘制纯色覆盖
        canvas.drawCircle(cx, cy, cRadius, mBitmapPaint!!)

        //使用宽高相等的椭圆为圆形来画边框
        if (borderWidth > 0) {
            val width = mBorderRect.width()
            val height = mBorderRect.height()
            val minSide = min(width.toDouble(), height.toDouble()).toFloat()
            val difX = (width - minSide) / 2
            val difY = (height - minSide) / 2

            // 将矩形调整为正方形并居中
            mBorderRect.left += difX
            mBorderRect.top += difY
            mBorderRect.right = mBorderRect.left + minSide
            mBorderRect.bottom = mBorderRect.top + minSide

            // 内缩边框宽度的一半
            mBorderRect.inset(borderWidth / 2, borderWidth / 2)

            // 绘制圆形边框
            mBorderPaint!!.strokeWidth = borderWidth
            canvas.drawOval(mBorderRect, mBorderPaint!!)
        }
        Log.i(TAG, "drawCircleRectBitmap end")
    }

    val state: IntArray
        get() = StateSet.WILD_CARD

    private class StopException : RuntimeException()

    init {
        init(context, attrs)
    }

    /**
     * 传入构造器，避免传统的设置一个参数调用一次invalidate()重新绘制
     */
    fun refreshView(builder: Builder?) {
        var isInvalidate = false
        if (builder == null) {
            return
        }
        if (builder.blurMode != -1 && this.blurShape != builder.blurMode) {
            this.blurShape = builder.blurMode
            isInvalidate = true
        }
        if (builder.mBorderColor != null && borderColor != builder.mBorderColor) {
            this.borderColor = builder.mBorderColor!!
            mBorderPaint!!.color =
                borderColor.getColorForState(state, DEFAULT_BORDER_COLOR)
            if (borderWidth > 0) {
                isInvalidate = true
            }
        }
        if (builder.mBorderWidth > 0) {
            borderWidth = builder.mBorderWidth
            mBorderPaint!!.strokeWidth = borderWidth
            isInvalidate = true
        }
        if (mCornerRadii[BlurCorner.TOP_LEFT] != builder.mCornerRadii[BlurCorner.TOP_LEFT] || mCornerRadii[BlurCorner.TOP_RIGHT] != builder.mCornerRadii[BlurCorner.TOP_RIGHT] || mCornerRadii[BlurCorner.BOTTOM_RIGHT] != builder.mCornerRadii[BlurCorner.BOTTOM_RIGHT] || mCornerRadii[BlurCorner.BOTTOM_LEFT] != builder.mCornerRadii[BlurCorner.BOTTOM_LEFT]) {
            mCornerRadii[BlurCorner.TOP_LEFT] = builder.mCornerRadii[BlurCorner.TOP_LEFT]
            mCornerRadii[BlurCorner.TOP_RIGHT] = builder.mCornerRadii[BlurCorner.TOP_RIGHT]
            mCornerRadii[BlurCorner.BOTTOM_LEFT] = builder.mCornerRadii[BlurCorner.BOTTOM_LEFT]
            mCornerRadii[BlurCorner.BOTTOM_RIGHT] = builder.mCornerRadii[BlurCorner.BOTTOM_RIGHT]
            isInvalidate = true
            initCornerRadius()
        }
        if (builder.mOverlayColor != -1 && mOverlayColor != builder.mOverlayColor) {
            mOverlayColor = builder.mOverlayColor
            isInvalidate = true
        }

        if (builder.mBlurRadius > 0 && mBlurRadius != builder.mBlurRadius) {
            mBlurRadius = builder.mBlurRadius
            isInvalidate = true
        }
        if (builder.mDownSampleFactor > 0 && mDownSampleFactor != builder.mDownSampleFactor) {
            mDownSampleFactor = builder.mDownSampleFactor
            isInvalidate = true
            releaseBitmap()
        }
        if (isInvalidate) {
            invalidate()
        }
    }

    /**
     * @noinspection unused
     */
    class Builder(context: Context) {
        // default 4
        var mDownSampleFactor: Float = -1f

        // default #aaffffff
        var mOverlayColor: Int = -1

        // default 10dp (0 < r <= 25)
        var mBlurRadius: Float = -1f
        var mBorderWidth: Float = -1f
        var mBorderColor: ColorStateList? = null
        var blurMode: Int = -1
        val mCornerRadii: FloatArray = floatArrayOf(0f, 0f, 0f, 0f)
        private val mContext: Context = context.applicationContext

        /**
         * 模糊半径
         *
         * @param radius 0~25
         */
        fun setBlurRadius(@FloatRange(from = 0.0, to = 25.0) radius: Float): Builder {
            mBlurRadius = radius
            return this
        }

        /**
         * 采样率
         *
         * @param factor 采样率
         */
        fun setDownSampleFactor(factor: Float): Builder {
            require(!(factor <= 0)) { "DownSample factor must be greater than 0." }
            mDownSampleFactor = factor
            return this
        }

        /**
         * 蒙层颜色
         *
         * @param color 蒙层颜色
         */
        fun setOverlayColor(color: Int): Builder {
            mOverlayColor = color
            return this
        }

        /**
         * Set the corner radius of a specific corner in px.
         * 设置圆角 圆形、椭圆无效
         *
         * @param corner 枚举类型 对应4个角
         * @param radius 角半径幅度
         */
        fun setCornerRadius(@BlurCorner corner: Int, radius: Float): Builder {
            mCornerRadii[corner] = radius
            return this
        }

        /**
         * Set all the corner radii from a dimension resource id.
         * 设置圆角 圆形、椭圆无效
         *
         * @param resId dimension resource id of radii.
         */
        fun setCornerRadiusDimen(@DimenRes resId: Int): Builder {
            val radius = mContext.resources.getDimension(resId)
            return setCornerRadius(radius, radius, radius, radius)
        }

        /**
         * Set the corner radius of a specific corner in px.
         * 设置圆角 圆形、椭圆无效
         *
         * @param radius 4个角同值
         */
        fun setCornerRadius(radius: Float): Builder {
            return setCornerRadius(radius, radius, radius, radius)
        }

        /**
         * Set the corner radius of a specific corner in px.
         * 设置圆角 圆形、椭圆无效
         */
        fun setCornerRadius(
            topLeft: Float, topRight: Float, bottomLeft: Float,
            bottomRight: Float
        ): Builder {
            mCornerRadii[BlurCorner.TOP_LEFT] = topLeft
            mCornerRadii[BlurCorner.TOP_RIGHT] = topRight
            mCornerRadii[BlurCorner.BOTTOM_LEFT] = bottomLeft
            mCornerRadii[BlurCorner.BOTTOM_RIGHT] = bottomRight
            return this
        }

        /**
         * 设置边框的宽度
         *
         * @param resId 资源ID
         */
        fun setBorderWidth(@DimenRes resId: Int): Builder {
            return setBorderWidth(mContext.resources.getDimension(resId))
        }

        /**
         * 设置边框的宽度
         *
         * @param width 转px值
         */
        fun setBorderWidth(width: Float): Builder {
            mBorderWidth = width
            return this
        }

        /**
         * 设置边框颜色
         *
         * @param color R.color.xxxx
         */
        fun setBorderColor(@ColorRes color: Int): Builder {
            return setBorderColor(ColorStateList.valueOf(ContextCompat.getColor(mContext, color)))
        }

        fun setBorderColor(colors: ColorStateList?): Builder {
            mBorderColor = if ((colors != null)) colors else ColorStateList.valueOf(
                DEFAULT_BORDER_COLOR
            )
            return this
        }

        /**
         * 设置高斯模糊的类型
         *
         * @param blurMode BlurMode枚举值，支持圆、方形、椭圆（宽高相等椭圆为圆）
         */
        fun setBlurMode(@BlurShape blurMode: Int): Builder {
            this.blurMode = blurMode
            return this
        }
    }

    companion object {
        private const val TAG = "ShapeBlurView"

        /**
         * 最大模糊半径
         */
        private const val MAX_BLUR_RADIUS = Toolkit.MAX_BLUR_RADIUS

        /**
         * 默认边框颜色为白色
         */
        const val DEFAULT_BORDER_COLOR: Int = Color.WHITE

        /**
         * 静态计数器，跟踪渲染次数
         */
        private var RENDERING_COUNT = 0

        /**
         * 默认圆角半径为0
         */
        private const val DEFAULT_RADIUS = 0f

        /**
         * 默认边框宽度为0
         */
        private const val DEFAULT_BORDER_WIDTH = 0f
        private val STOP_EXCEPTION = StopException()

        /**
         * 建造者模式，避免设置一个参数调用一次重新绘制
         */
        fun build(context: Context): Builder {
            return Builder(context)
        }
    }
}
