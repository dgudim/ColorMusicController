package com.deo.colorcontrol;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import static java.lang.StrictMath.abs;

public class Utils {
    
    static TextureRegionDrawable generateRegion(int width, int height, Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        TextureRegionDrawable textureRegionDrawable = new TextureRegionDrawable(new TextureRegion(new Texture(pixmap)));
        pixmap.dispose();
        return textureRegionDrawable;
    }
    
    static void setDrawableDimensions(float width, float height, Drawable... drawables) {
        for (Drawable drawable : drawables) {
            drawable.setMinWidth(width);
            drawable.setMinHeight(height);
        }
    }
    
    private static float getNeighbours(float[] array, int index, int offset) {
        float sum = 0;
        for (int i = -offset; i < offset; i++) {
            sum += (i + index > 0 && i + index < array.length) ? abs(array[i + index]) : 0;
        }
        return sum;
    }
    
    static void applyLinearScale(float[] array, float slope) {
        for (int i = 0; i < array.length; i++) {
            array[i] *= Math.log(i) * (i * slope + 1);
        }
    }
    
    static float[] smoothArray(float[] array, int smoothingRange, int smoothingFactor, boolean newArray) {
        float[] samplesNew = new float[0];
        if (newArray) {
            samplesNew = new float[array.length];
        }
        for (int t = 0; t < smoothingFactor; t++) {
            for (int i = 0; i < array.length; i++) {
                if (newArray) {
                    samplesNew[i] = getNeighbours(array, i, smoothingRange) / (float) (smoothingRange * 2 + 1);
                } else {
                    array[i] = getNeighbours(array, i, smoothingRange) / (float) (smoothingRange * 2 + 1);
                }
            }
        }
        if (newArray) {
            return samplesNew;
        } else {
            return array;
        }
    }
    
    static void shiftArray(int times, float[]... arrays) {
        for (int t = 0; t < times; t++) {
            for (float[] array : arrays) {
                if (array.length - 1 >= 0)
                    System.arraycopy(array, 0, array, 1, array.length - 1);
            }
        }
    }
}
