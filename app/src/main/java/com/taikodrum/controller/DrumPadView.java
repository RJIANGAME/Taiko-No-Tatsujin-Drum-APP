package com.taikodrum.controller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class DrumPadView extends View {
    public enum Zone {
        LEFT_RIM("D", "KA", "LEFT RIM"),
        LEFT_CENTER("F", "DON", "LEFT DRUM"),
        RIGHT_CENTER("J", "DON", "RIGHT DRUM"),
        RIGHT_RIM("K", "KA", "RIGHT RIM");

        public final String key;
        public final String hitName;
        public final String label;

        Zone(String key, String hitName, String label) {
            this.key = key;
            this.hitName = hitName;
            this.label = label;
        }
    }

    interface HitListener {
        void onHit(Zone zone, boolean pressed);
    }

    private static final float LEFT_RIM_END = 0.22f;
    private static final float LEFT_CENTER_END = 0.50f;
    private static final float RIGHT_CENTER_END = 0.78f;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseArray<Zone> activePointers = new SparseArray<>();
    private final RectF rect = new RectF();
    private final RectF oval = new RectF();
    private final Path path = new Path();

    private HitListener hitListener;

    public DrumPadView(Context context) {
        super(context);
        init();
    }

    public DrumPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void setHitListener(HitListener hitListener) {
        this.hitListener = hitListener;
    }

    void releaseAll() {
        for (int i = 0; i < activePointers.size(); i++) {
            Zone zone = activePointers.valueAt(i);
            notifyHit(zone, false);
        }
        activePointers.clear();
        invalidate();
    }

    private void init() {
        setFocusable(true);
        setClickable(true);
        setBackgroundColor(Color.rgb(39, 12, 10));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setSubpixelText(true);

        activePaint.setColor(Color.argb(86, 255, 255, 255));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        drawBackground(canvas, width, height);

        float topBand = Math.max(dp(24), height * 0.10f);
        float bottomBand = Math.max(dp(18), height * 0.07f);
        float top = topBand + dp(8);
        float padHeight = height - topBand - bottomBand - dp(16);

        drawZone(canvas, Zone.LEFT_RIM, 0f, width * LEFT_RIM_END, top, padHeight);
        drawZone(canvas, Zone.LEFT_CENTER, width * LEFT_RIM_END, width * LEFT_CENTER_END, top, padHeight);
        drawZone(canvas, Zone.RIGHT_CENTER, width * LEFT_CENTER_END, width * RIGHT_CENTER_END, top, padHeight);
        drawZone(canvas, Zone.RIGHT_RIM, width * RIGHT_CENTER_END, width, top, padHeight);

        drawCenterFrame(canvas, width, top, padHeight);
        drawSeparators(canvas, width, top, padHeight);
        drawLabels(canvas, width, height, top, padHeight);
        drawActiveTouches(canvas, width, top, padHeight);
    }

    private void drawBackground(Canvas canvas, int width, int height) {
        fillPaint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                Color.rgb(88, 20, 14),
                Color.rgb(24, 10, 11),
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0f, 0f, width, height, fillPaint);
        fillPaint.setShader(null);

        float band = Math.max(dp(28), height * 0.10f);
        fillPaint.setColor(Color.rgb(255, 205, 86));
        canvas.drawRect(0f, 0f, width, band * 0.34f, fillPaint);
        fillPaint.setColor(Color.rgb(250, 174, 43));
        canvas.drawRect(0f, height - band * 0.24f, width, height, fillPaint);

        drawFestivalTriangles(canvas, width, 0f, band, true);
        drawFestivalTriangles(canvas, width, height - band * 0.72f, band, false);

        fillPaint.setColor(Color.argb(34, 255, 236, 150));
        float dotGap = Math.max(dp(34), width / 18f);
        for (float x = dotGap * 0.5f; x < width; x += dotGap) {
            canvas.drawCircle(x, band * 0.63f, dp(3.3f), fillPaint);
            canvas.drawCircle(x + dotGap * 0.48f, height - band * 0.36f, dp(2.6f), fillPaint);
        }
    }

    private void drawFestivalTriangles(Canvas canvas, int width, float y, float band, boolean down) {
        float tile = Math.max(dp(34), width / 16f);
        int[] colors = {
                Color.rgb(232, 51, 38),
                Color.rgb(255, 247, 204),
                Color.rgb(33, 142, 232),
                Color.rgb(255, 183, 47)
        };

        for (float x = -tile; x < width + tile; x += tile) {
            int colorIndex = Math.abs((int) (x / tile)) % colors.length;
            fillPaint.setColor(colors[colorIndex]);
            path.reset();
            if (down) {
                path.moveTo(x, y);
                path.lineTo(x + tile, y);
                path.lineTo(x + tile * 0.5f, y + band * 0.72f);
            } else {
                path.moveTo(x, y + band * 0.72f);
                path.lineTo(x + tile, y + band * 0.72f);
                path.lineTo(x + tile * 0.5f, y);
            }
            path.close();
            canvas.drawPath(path, fillPaint);
        }
    }

    private void drawZone(Canvas canvas, Zone zone, float left, float right, float top, float height) {
        float gap = dp(5);
        float radius = dp(22);
        float zoneLeft = left + gap;
        float zoneRight = right - gap;

        fillPaint.setShader(null);
        fillPaint.setColor(Color.argb(110, 0, 0, 0));
        rect.set(zoneLeft + dp(3), top + dp(6), zoneRight + dp(3), top + height + dp(6));
        canvas.drawRoundRect(rect, radius, radius, fillPaint);

        int startColor;
        int midColor;
        int endColor;
        if (isRim(zone)) {
            startColor = Color.rgb(9, 84, 190);
            midColor = Color.rgb(22, 153, 239);
            endColor = Color.rgb(102, 214, 255);
        } else {
            startColor = Color.rgb(188, 23, 25);
            midColor = Color.rgb(247, 66, 31);
            endColor = Color.rgb(255, 169, 43);
        }

        fillPaint.setShader(new LinearGradient(
                zoneLeft,
                top,
                zoneRight,
                top + height,
                new int[]{startColor, midColor, endColor},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        ));
        rect.set(zoneLeft, top, zoneRight, top + height);
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        fillPaint.setShader(null);

        drawZoneHighlight(canvas, zone, zoneLeft, zoneRight, top, height);
        if (isRim(zone)) {
            drawRimTexture(canvas, zoneLeft, zoneRight, top, height);
        } else {
            drawDrumTexture(canvas, zoneLeft, zoneRight, top, height);
        }
    }

    private void drawZoneHighlight(Canvas canvas, Zone zone, float left, float right, float top, float height) {
        fillPaint.setColor(isRim(zone) ? Color.argb(56, 255, 255, 255) : Color.argb(68, 255, 238, 180));
        oval.set(left + dp(14), top + dp(12), right - dp(14), top + height * 0.36f);
        canvas.drawOval(oval, fillPaint);

        fillPaint.setColor(Color.argb(34, 0, 0, 0));
        oval.set(left + dp(10), top + height * 0.66f, right - dp(10), top + height - dp(12));
        canvas.drawOval(oval, fillPaint);
    }

    private void drawRimTexture(Canvas canvas, float left, float right, float top, float height) {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(4));
        strokePaint.setColor(Color.argb(118, 255, 255, 255));

        float centerX = (left + right) * 0.5f;
        for (int i = 0; i < 4; i++) {
            float inset = dp(18 + i * 13);
            oval.set(left + inset, top + height * 0.18f + i * dp(5), right - inset, top + height * 0.82f - i * dp(5));
            canvas.drawArc(oval, 206, 128, false, strokePaint);
            canvas.drawArc(oval, -26, 128, false, strokePaint);
        }

        fillPaint.setColor(Color.argb(90, 255, 245, 185));
        canvas.drawCircle(centerX, top + height * 0.50f, dp(7), fillPaint);
        canvas.drawCircle(centerX, top + height * 0.50f, dp(3), fillPaint);
    }

    private void drawDrumTexture(Canvas canvas, float left, float right, float top, float height) {
        float centerX = (left + right) * 0.5f;
        float centerY = top + height * 0.50f;
        float radius = Math.min((right - left) * 0.46f, height * 0.44f);

        fillPaint.setShader(new RadialGradient(
                centerX,
                centerY,
                radius,
                new int[]{
                        Color.argb(94, 255, 245, 198),
                        Color.argb(30, 255, 255, 255),
                        Color.argb(72, 105, 20, 8)
                },
                new float[]{0f, 0.66f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(centerX, centerY, radius, fillPaint);
        fillPaint.setShader(null);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(3));
        strokePaint.setColor(Color.argb(116, 255, 232, 157));
        canvas.drawCircle(centerX, centerY, radius * 0.72f, strokePaint);
        strokePaint.setStrokeWidth(dp(1.5f));
        strokePaint.setColor(Color.argb(82, 106, 24, 14));
        canvas.drawCircle(centerX, centerY, radius * 0.46f, strokePaint);
    }

    private void drawCenterFrame(Canvas canvas, int width, float top, float height) {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(5));
        strokePaint.setColor(Color.argb(210, 255, 238, 176));
        rect.set(width * LEFT_RIM_END + dp(6), top + dp(6), width * RIGHT_CENTER_END - dp(6), top + height - dp(6));
        canvas.drawRoundRect(rect, dp(24), dp(24), strokePaint);

        strokePaint.setStrokeWidth(dp(2));
        strokePaint.setColor(Color.argb(138, 111, 42, 18));
        rect.inset(dp(8), dp(8));
        canvas.drawRoundRect(rect, dp(18), dp(18), strokePaint);
    }

    private void drawSeparators(Canvas canvas, int width, float top, float height) {
        float[] xs = {
                width * LEFT_RIM_END,
                width * LEFT_CENTER_END,
                width * RIGHT_CENTER_END
        };

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(3));
        strokePaint.setColor(Color.argb(160, 255, 246, 202));
        for (float x : xs) {
            canvas.drawLine(x, top + dp(20), x, top + height - dp(20), strokePaint);
        }
    }

    private void drawLabels(Canvas canvas, int width, int height, float top, float padHeight) {
        float keySize = Math.max(dp(44), Math.min(dp(92), height * 0.25f));
        float hitSize = Math.max(dp(20), Math.min(dp(36), height * 0.10f));
        float zoneSize = Math.max(dp(11), Math.min(dp(16), height * 0.045f));

        drawLabel(canvas, Zone.LEFT_RIM, width * (LEFT_RIM_END / 2f), top, padHeight, keySize, hitSize, zoneSize);
        drawLabel(canvas, Zone.LEFT_CENTER, width * ((LEFT_RIM_END + LEFT_CENTER_END) / 2f), top, padHeight, keySize, hitSize, zoneSize);
        drawLabel(canvas, Zone.RIGHT_CENTER, width * ((LEFT_CENTER_END + RIGHT_CENTER_END) / 2f), top, padHeight, keySize, hitSize, zoneSize);
        drawLabel(canvas, Zone.RIGHT_RIM, width * ((RIGHT_CENTER_END + 1f) / 2f), top, padHeight, keySize, hitSize, zoneSize);
    }

    private void drawLabel(Canvas canvas, Zone zone, float centerX, float top, float height, float keySize, float hitSize, float zoneSize) {
        textPaint.setTextSize(zoneSize);
        textPaint.setColor(Color.argb(218, 255, 246, 210));
        canvas.drawText(zone.label, centerX, top + height * 0.18f, textPaint);

        textPaint.setTextSize(keySize);
        textPaint.setColor(Color.WHITE);
        textPaint.setShadowLayer(dp(3), 0f, dp(2), Color.argb(170, 74, 20, 8));
        canvas.drawText(zone.key, centerX, top + height * 0.52f, textPaint);
        textPaint.clearShadowLayer();

        float badgeWidth = Math.max(dp(70), keySize * 1.55f);
        float badgeHeight = Math.max(dp(28), hitSize * 1.34f);
        rect.set(centerX - badgeWidth * 0.5f, top + height * 0.62f, centerX + badgeWidth * 0.5f, top + height * 0.62f + badgeHeight);
        fillPaint.setColor(isRim(zone) ? Color.rgb(5, 76, 172) : Color.rgb(172, 28, 22));
        canvas.drawRoundRect(rect, badgeHeight * 0.5f, badgeHeight * 0.5f, fillPaint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2));
        strokePaint.setColor(Color.argb(180, 255, 246, 204));
        canvas.drawRoundRect(rect, badgeHeight * 0.5f, badgeHeight * 0.5f, strokePaint);

        textPaint.setTextSize(hitSize);
        textPaint.setColor(Color.rgb(255, 246, 215));
        canvas.drawText(zone.hitName, centerX, rect.centerY() + hitSize * 0.34f, textPaint);
    }

    private void drawActiveTouches(Canvas canvas, int width, float top, float height) {
        for (int i = 0; i < activePointers.size(); i++) {
            Zone zone = activePointers.valueAt(i);
            float left;
            float right;
            if (zone == Zone.LEFT_RIM) {
                left = 0f;
                right = width * LEFT_RIM_END;
            } else if (zone == Zone.LEFT_CENTER) {
                left = width * LEFT_RIM_END;
                right = width * LEFT_CENTER_END;
            } else if (zone == Zone.RIGHT_CENTER) {
                left = width * LEFT_CENTER_END;
                right = width * RIGHT_CENTER_END;
            } else {
                left = width * RIGHT_CENTER_END;
                right = width;
            }

            float gap = dp(8);
            rect.set(left + gap, top + gap, right - gap, top + height - gap);
            canvas.drawRoundRect(rect, dp(18), dp(18), activePaint);
            drawHitBurst(canvas, (left + right) * 0.5f, top + height * 0.48f, Math.min(right - left, height) * 0.32f);
        }
    }

    private void drawHitBurst(Canvas canvas, float centerX, float centerY, float radius) {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(4));
        strokePaint.setColor(Color.argb(210, 255, 248, 195));
        canvas.drawCircle(centerX, centerY, radius, strokePaint);

        strokePaint.setStrokeWidth(dp(3));
        for (int i = 0; i < 12; i++) {
            double angle = Math.toRadians(i * 30);
            float x1 = centerX + (float) Math.cos(angle) * radius * 0.70f;
            float y1 = centerY + (float) Math.sin(angle) * radius * 0.70f;
            float x2 = centerX + (float) Math.cos(angle) * radius * 1.06f;
            float y2 = centerY + (float) Math.sin(angle) * radius * 1.06f;
            canvas.drawLine(x1, y1, x2, y2, strokePaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleDown(event, index);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                handleUp(event, index);
                return true;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                return true;
            default:
                return true;
        }
    }

    private void handleDown(MotionEvent event, int index) {
        int pointerId = event.getPointerId(index);
        Zone zone = zoneForX(event.getX(index));
        activePointers.put(pointerId, zone);
        performHapticFeedbackCompat();
        notifyHit(zone, true);
        invalidate();
    }

    private void handleUp(MotionEvent event, int index) {
        int pointerId = event.getPointerId(index);
        Zone zone = activePointers.get(pointerId);
        if (zone != null) {
            notifyHit(zone, false);
            activePointers.remove(pointerId);
            invalidate();
        }
    }

    private Zone zoneForX(float x) {
        float normalized = getWidth() <= 0 ? 0f : x / getWidth();
        if (normalized < LEFT_RIM_END) {
            return Zone.LEFT_RIM;
        }
        if (normalized < LEFT_CENTER_END) {
            return Zone.LEFT_CENTER;
        }
        if (normalized < RIGHT_CENTER_END) {
            return Zone.RIGHT_CENTER;
        }
        return Zone.RIGHT_RIM;
    }

    private boolean isRim(Zone zone) {
        return zone == Zone.LEFT_RIM || zone == Zone.RIGHT_RIM;
    }

    private void notifyHit(Zone zone, boolean pressed) {
        if (hitListener != null) {
            hitListener.onHit(zone, pressed);
        }
    }

    private void performHapticFeedbackCompat() {
        int feedback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? HapticFeedbackConstants.KEYBOARD_TAP
                : HapticFeedbackConstants.VIRTUAL_KEY;
        performHapticFeedback(feedback);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
