/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devgxy.blur.demo;


import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
/**
 * Horizontally lay out children until the row is filled and then moved to the next line. Call
 * {@link FlowLayout#setSingleLine(boolean)} to disable reflow and lay all children out in one line.
 */
public class FlowLayout extends ViewGroup {
    private int lineSpacing;
    private int itemSpacing;
    private boolean singleLine;
    private int rowCount;

    public FlowLayout(@NonNull Context context) {
        this(context, null);
    }

    public FlowLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        singleLine = false;
        loadFromAttributes(context, attrs);
    }

    public FlowLayout(
        @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        singleLine = false;
        loadFromAttributes(context, attrs);
    }

    private void loadFromAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        TypedArray array = context.getTheme()
            .obtainStyledAttributes(attrs, R.styleable.FlowLayout, 0, 0);
        try {
            lineSpacing = array.getDimensionPixelSize(R.styleable.FlowLayout_lineSpacing, 0);
            itemSpacing = array.getDimensionPixelSize(R.styleable.FlowLayout_itemSpacing, 0);
        } finally {
            array.recycle();
        }
    }

    protected int getLineSpacing() {
        return lineSpacing;
    }

    protected void setLineSpacing(int lineSpacing) {
        this.lineSpacing = lineSpacing;
        requestLayout();
    }

    protected int getItemSpacing() {
        return itemSpacing;
    }

    protected void setItemSpacing(int itemSpacing) {
        this.itemSpacing = itemSpacing;
        requestLayout();
    }

    public boolean isSingleLine() {
        return singleLine;
    }

    public void setSingleLine(boolean singleLine) {
        this.singleLine = singleLine;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);

        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        final int maxWidth = widthMode == MeasureSpec.AT_MOST || widthMode == MeasureSpec.EXACTLY
            ? widthSize
            : Integer.MAX_VALUE;

        int childLeft = getPaddingLeft();
        int childTop = getPaddingTop();
        int maxChildRight = 0;
        int lineHeight = 0;
        int totalHeight = childTop;

        final int maxRight = maxWidth - getPaddingRight();

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);

            if (child.getVisibility() == View.GONE) {
                continue;
            }

            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            int leftMargin = lp.leftMargin;
            int rightMargin = lp.rightMargin;
            int topMargin = lp.topMargin;
            int bottomMargin = lp.bottomMargin;

            int childRight = childLeft + leftMargin + childWidth + rightMargin;

            if (childRight > maxRight && !singleLine) {
                // Move to next line
                childLeft = getPaddingLeft();
                childTop = totalHeight + lineSpacing;
                lineHeight = 0;
                childRight = childLeft + leftMargin + childWidth + rightMargin;
            }

            lineHeight = Math.max(lineHeight, topMargin + childHeight + bottomMargin);

            maxChildRight = Math.max(maxChildRight, childRight);
            childLeft += leftMargin + childWidth + rightMargin + itemSpacing;

            totalHeight = childTop + lineHeight;
        }

        maxChildRight += getPaddingRight();
        totalHeight += getPaddingBottom();

        int finalWidth = getMeasuredDimension(widthSize, widthMode, maxChildRight);
        int finalHeight = getMeasuredDimension(heightSize, heightMode, totalHeight);

        setMeasuredDimension(finalWidth, finalHeight);
    }

    private static int getMeasuredDimension(int size, int mode, int childrenEdge) {
        return switch (mode) {
            case MeasureSpec.EXACTLY -> size;
            case MeasureSpec.AT_MOST -> Math.min(childrenEdge, size);
            default -> // UNSPECIFIED:
                childrenEdge;
        };
    }

    @Override
    protected void onLayout(boolean sizeChanged, int left, int top, int right, int bottom) {
        if (getChildCount() == 0) {
            // Do not re-layout when there are no children.
            rowCount = 0;
            return;
        }
        rowCount = 1;

        boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        int paddingStart = isRtl ? getPaddingRight() : getPaddingLeft();
        int paddingEnd = isRtl ? getPaddingLeft() : getPaddingRight();
        int childStart = paddingStart;
        int childTop = getPaddingTop();

        final int maxChildEnd = right - left - paddingEnd;
        int lineHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);

            if (child.getVisibility() == View.GONE) {
                child.setTag(R.id.row_index_key, -1);
                continue;
            }

            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            int startMargin = lp.getMarginStart();
            int endMargin = lp.getMarginEnd();
            int topMargin = lp.topMargin;
            int bottomMargin = lp.bottomMargin;

            int childEnd = childStart + startMargin + childWidth + endMargin;

            if (!singleLine && (childEnd > maxChildEnd)) {
                childStart = paddingStart;
                childTop += lineHeight + lineSpacing;
                lineHeight = 0;
                rowCount++;
            }

            lineHeight = Math.max(lineHeight, topMargin + childHeight + bottomMargin);

            int childLeft, childRight;
            if (isRtl) {
                childRight = maxChildEnd - (childStart + startMargin);
                childLeft = childRight - childWidth;
            } else {
                childLeft = childStart + startMargin;
                childRight = childLeft + childWidth;
            }

            int childBottom = childTop + topMargin + childHeight;

            child.layout(childLeft, childTop + topMargin, childRight, childBottom);

            child.setTag(R.id.row_index_key, rowCount - 1);

            childStart += startMargin + childWidth + endMargin + itemSpacing;
        }
    }

    protected int getRowCount() {
        return rowCount;
    }

    public int getRowIndex(@NonNull View child) {
        Object index = child.getTag(R.id.row_index_key);
        if (!(index instanceof Integer)) {
            return -1;
        }
        return (int) index;
    }

    // Override methods to support MarginLayoutParams
    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams lp) {
        return new MarginLayoutParams(lp);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }
}

