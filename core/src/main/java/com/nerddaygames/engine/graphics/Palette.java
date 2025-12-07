package com.nerddaygames.engine.graphics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Disposable;

public class Palette implements Disposable {
    private final Color[] colors;

    // 32-Color "NerdOS" Default Palette
    // Format: RRGGBB (Hex)
    private static final String[] DEFAULT_HEX = {
        // ROW 1 (Dark / Backgrounds)
        "000000", // 0: Black
        "1D2B53", // 1: Dark Blue
        "7E2553", // 2: Dark Purple
        "008751", // 3: Dark Green
        "AB5236", // 4: Brown
        "5F574F", // 5: Dark Gray
        "C2C3C7", // 6: Light Gray
        "FFF1E8", // 7: White

        // ROW 2 (Bright / Accents)
        "FF004D", // 8: Red
        "FFA300", // 9: Orange
        "FFEC27", // 10: Yellow
        "00E436", // 11: Green
        "29ADFF", // 12: Blue
        "83769C", // 13: Indigo
        "FF77A8", // 14: Pink
        "FFCCAA", // 15: Peach

        // ROW 3 (Extended / User Interface)
        "29222E", // 16: Deep Charcoal (UI BG)
        "3D3447", // 17: Darker Gray
        "564D61", // 18: Medium Gray
        "746D7F", // 19: Light Blue-Gray
        "8F929E", // 20: Steel
        "A5B4C4", // 21: Highlight
        "58929A", // 22: Muted Teal
        "285C66", // 23: Dark Teal

        // ROW 4 (Vibrant Extras)
        "8A2735", // 24: Dark Red
        "D44E31", // 25: Burnt Orange
        "E09F36", // 26: Gold
        "94C93D", // 27: Lime
        "3D6E70", // 28: Slate
        "4B692F", // 29: Olive
        "37233B", // 30: Deep Violet
        "E37868"  // 31: Salmon
    };

    public Palette() {
        // Initialize the array
        this.colors = new Color[DEFAULT_HEX.length];

        // Parse Hex Strings into LibGDX Colors
        for (int i = 0; i < DEFAULT_HEX.length; i++) {
            // Color.valueOf handles "RRGGBB" strings automatically
            colors[i] = Color.valueOf(DEFAULT_HEX[i]);
        }
    }

    public Color get(int index) {
        // Safety: wrap around or clamp if index is too high
        if (index < 0) index = 0;
        if (index >= colors.length) index = index % colors.length;

        return colors[index];
    }

    public int size() { return colors.length; }

    public int rgbaToIndex(int rgba8888) {
        // Extract RGB components (ignore alpha for now)
        int r = (rgba8888 >>> 24) & 0xFF;
        int g = (rgba8888 >>> 16) & 0xFF;
        int b = (rgba8888 >>> 8) & 0xFF;

        // Find closest color in palette
        int bestMatch = 0;
        float bestDistance = Float.MAX_VALUE;

        for (int i = 0; i < colors.length; i++) {
            Color c = colors[i];
            int cr = (int)(c.r * 255);
            int cg = (int)(c.g * 255);
            int cb = (int)(c.b * 255);

            // Euclidean distance in RGB space
            int dr = r - cr;
            int dg = g - cg;
            int db = b - cb;
            float distance = dr*dr + dg*dg + db*db;

            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = i;
            }
        }

        return bestMatch;
    }

    @Override
    public void dispose() {
        // No texture to dispose anymore!
    }
}
