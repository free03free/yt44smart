package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.loading;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.prefs.LoadingSpinnerData;

/**
 * Animated loading indicators drawn with the canvas. The style is chosen in the app settings.
 */
public class LoadingSpinnerDrawable extends Drawable implements Animatable {
    private static final float TWO_PI = (float) (Math.PI * 2);

    private final int mStyle;
    private final int mColor;
    private final int mSizePx;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private int mAlpha = 255;
    private float mProgress;
    private ValueAnimator mAnimator;
    private SweepGradient mGradient;

    private LoadingSpinnerDrawable(int style, int color, int sizePx) {
        mStyle = style;
        mColor = color;
        mSizePx = sizePx;
    }

    /**
     * @return the drawable of the style chosen in settings or null when the default (system) indicator should be used
     */
    @Nullable
    public static Drawable createFromPrefs(Context context) {
        LoadingSpinnerData data = LoadingSpinnerData.instance(context);
        int style = data.getStyle();

        if (style == LoadingSpinnerData.STYLE_DEFAULT) {
            return null;
        }

        int sizePx = (int) (context.getResources().getDisplayMetrics().density * data.getSizeDp());
        int color = data.getColor() != 0 ? data.getColor() : resolveColor(context);

        return new LoadingSpinnerDrawable(style, color, sizePx);
    }

    private static int resolveColor(Context context) {
        try {
            TypedValue value = new TypedValue();
            if (context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true)) {
                if (value.resourceId != 0) {
                    ColorStateList list = ContextCompat.getColorStateList(context, value.resourceId);
                    if (list != null) {
                        return list.getDefaultColor() | 0xFF000000;
                    }
                } else if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return value.data | 0xFF000000;
                }
            }
        } catch (Exception e) {
            // fall through
        }

        return Color.WHITE;
    }

    // Animation

    @Override
    public void start() {
        if (mAnimator != null && mAnimator.isRunning()) {
            return;
        }

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(getCycleMs());
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(animation -> {
            mProgress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        mAnimator.start();
    }

    @Override
    public void stop() {
        if (mAnimator != null) {
            mAnimator.removeAllUpdateListeners();
            mAnimator.cancel();
            mAnimator = null;
        }
    }

    @Override
    public boolean isRunning() {
        return mAnimator != null && mAnimator.isRunning();
    }

    private long getCycleMs() {
        switch (mStyle) {
            case LoadingSpinnerData.STYLE_ARC_GRADIENT:
                return 900;
            case LoadingSpinnerData.STYLE_RING_ARC:
            case LoadingSpinnerData.STYLE_TWO_ARCS:
                return 1100;
            default:
                return 1000;
        }
    }

    // Drawable

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mGradient = null;
    }

    @Override
    public int getIntrinsicWidth() {
        return mSizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return mSizePx;
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float r = Math.min(bounds.width(), bounds.height()) / 2f * 0.92f;

        mPaint.setShader(null);

        switch (mStyle) {
            case LoadingSpinnerData.STYLE_BARS_12:
                drawBars(canvas, cx, cy, 12, r * 0.52f, r, r * 0.15f);
                break;
            case LoadingSpinnerData.STYLE_BARS_THICK:
                drawBars(canvas, cx, cy, 8, r * 0.5f, r, r * 0.27f);
                break;
            case LoadingSpinnerData.STYLE_STARBURST:
                drawBars(canvas, cx, cy, 12, r * 0.22f, r, r * 0.07f);
                break;
            case LoadingSpinnerData.STYLE_DOTS_FADE:
                drawDots(canvas, cx, cy, 8, r * 0.78f, r * 0.15f, DOTS_FADE);
                break;
            case LoadingSpinnerData.STYLE_DOTS_TAPER:
                drawDots(canvas, cx, cy, 10, r * 0.78f, r * 0.17f, DOTS_TAPER);
                break;
            case LoadingSpinnerData.STYLE_DOTS_PULSE:
                drawDots(canvas, cx, cy, 8, r * 0.78f, r * 0.17f, DOTS_PULSE);
                break;
            case LoadingSpinnerData.STYLE_ARC_GRADIENT:
                drawGradientArc(canvas, cx, cy, r, r * 0.22f);
                break;
            case LoadingSpinnerData.STYLE_RING_ARC:
                drawRingWithArcs(canvas, cx, cy, r, r * 0.13f, 1, 90f);
                break;
            case LoadingSpinnerData.STYLE_DASHES:
                drawBars(canvas, cx, cy, 16, r * 0.72f, r, r * 0.09f);
                break;
            case LoadingSpinnerData.STYLE_SEGMENTS:
                drawSegments(canvas, cx, cy, r, r * 0.22f);
                break;
            case LoadingSpinnerData.STYLE_TWO_ARCS:
                drawRingWithArcs(canvas, cx, cy, r, r * 0.13f, 2, 100f);
                break;
            default:
                break;
        }
    }

    // Painters

    private int color(float alpha) {
        float a = Math.max(0f, Math.min(1f, alpha)) * mAlpha;

        return (mColor & 0x00FFFFFF) | (((int) a) << 24);
    }

    /** Index of the brightest element for the stepped animations */
    private int head(int count) {
        return ((int) (mProgress * count)) % count;
    }

    private void drawBars(Canvas canvas, float cx, float cy, int count, float innerR, float outerR, float strokeW) {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(strokeW);

        int head = head(count);

        for (int i = 0; i < count; i++) {
            int age = (head - i + count) % count;
            mPaint.setColor(color(1f - 0.85f * age / count));

            canvas.save();
            canvas.rotate(i * 360f / count, cx, cy);
            canvas.drawLine(cx, cy - outerR + strokeW / 2, cx, cy - innerR - strokeW / 2, mPaint);
            canvas.restore();
        }
    }

    private static final int DOTS_FADE = 0;
    private static final int DOTS_TAPER = 1;
    private static final int DOTS_PULSE = 2;

    private void drawDots(Canvas canvas, float cx, float cy, int count, float ringR, float dotR, int mode) {
        mPaint.setStyle(Paint.Style.FILL);

        int head = head(count);

        for (int i = 0; i < count; i++) {
            float angle = TWO_PI * i / count - TWO_PI / 4f;
            float x = cx + ringR * (float) Math.cos(angle);
            float y = cy + ringR * (float) Math.sin(angle);
            float radius = dotR;
            float alpha;

            if (mode == DOTS_PULSE) {
                float phase = ((mProgress - (float) i / count) % 1f + 1f) % 1f;
                float scale = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.cos(TWO_PI * phase));
                radius = dotR * scale;
                alpha = 0.4f + 0.6f * scale;
            } else {
                int age = (head - i + count) % count;
                alpha = 1f - 0.8f * age / count;
                if (mode == DOTS_TAPER) {
                    radius = dotR * (1f - 0.7f * age / count);
                }
            }

            mPaint.setColor(color(alpha));
            canvas.drawCircle(x, y, radius, mPaint);
        }
    }

    private void drawGradientArc(Canvas canvas, float cx, float cy, float r, float strokeW) {
        if (mGradient == null) {
            mGradient = new SweepGradient(cx, cy, new int[]{mColor & 0x00FFFFFF, mColor | 0xFF000000}, new float[]{0f, 0.75f});
        }

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(strokeW);
        mPaint.setShader(mGradient);
        mPaint.setAlpha(mAlpha);

        mRect.set(cx - r + strokeW / 2, cy - r + strokeW / 2, cx + r - strokeW / 2, cy + r - strokeW / 2);

        canvas.save();
        canvas.rotate(mProgress * 360f, cx, cy);
        canvas.drawArc(mRect, 0f, 270f, false, mPaint);
        canvas.restore();

        mPaint.setShader(null);
    }

    private void drawRingWithArcs(Canvas canvas, float cx, float cy, float r, float strokeW, int arcs, float sweep) {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(strokeW);

        float ringR = r - strokeW / 2;
        mRect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);

        // faint track
        mPaint.setColor(color(0.2f));
        canvas.drawCircle(cx, cy, ringR, mPaint);

        // rotating arcs
        mPaint.setColor(color(1f));
        canvas.save();
        canvas.rotate(mProgress * 360f, cx, cy);
        for (int i = 0; i < arcs; i++) {
            canvas.drawArc(mRect, i * 360f / arcs, sweep, false, mPaint);
        }
        canvas.restore();
    }

    private void drawSegments(Canvas canvas, float cx, float cy, float r, float strokeW) {
        final int count = 8;
        final float sweep = 30f;

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
        mPaint.setStrokeWidth(strokeW);

        float ringR = r - strokeW / 2;
        mRect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);

        int head = head(count);

        for (int i = 0; i < count; i++) {
            int age = (head - i + count) % count;
            mPaint.setColor(color(1f - 0.8f * age / count));
            float start = i * 360f / count - 90f - sweep / 2f;
            canvas.drawArc(mRect, start, sweep, false, mPaint);
        }
    }
}
