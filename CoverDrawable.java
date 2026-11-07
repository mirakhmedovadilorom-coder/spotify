package dev.mirahmad.volna;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Обложка, нарисованная кодом: градиент от названия + первая буква. */
public class CoverDrawable extends Drawable {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int c1, c2;
    private final String letter;
    private final float radiusFrac;

    public CoverDrawable(String seed, float radiusFrac) {
        int h = Math.abs(seed.hashCode()) % 360;
        c1 = Color.HSVToColor(new float[]{h, 0.65f, 0.85f});
        c2 = Color.HSVToColor(new float[]{(h + 45) % 360, 0.80f, 0.45f});
        letter = seed.isEmpty() ? "♪" : seed.substring(0, 1).toUpperCase();
        this.radiusFrac = radiusFrac;
        txt.setColor(Color.argb(230, 255, 255, 255));
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
    }

    @Override
    public void draw(Canvas canvas) {
        RectF r = new RectF(getBounds());
        if (r.width() <= 0) return;
        bg.setShader(new LinearGradient(r.left, r.top, r.right, r.bottom, c1, c2, Shader.TileMode.CLAMP));
        float rad = Math.min(r.width(), r.height()) * radiusFrac;
        canvas.drawRoundRect(r, rad, rad, bg);
        txt.setTextSize(r.height() * 0.5f);
        float y = r.centerY() - (txt.descent() + txt.ascent()) / 2f;
        canvas.drawText(letter, r.centerX(), y, txt);
    }

    public Bitmap toBitmap(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        setBounds(0, 0, size, size);
        draw(new Canvas(b));
        return b;
    }

    // Квадрат с «внутренним» размером — чтобы ImageView не растягивал обложку в прямоугольник
    @Override public int getIntrinsicWidth() { return 512; }
    @Override public int getIntrinsicHeight() { return 512; }

    @Override public void setAlpha(int alpha) { bg.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { bg.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
