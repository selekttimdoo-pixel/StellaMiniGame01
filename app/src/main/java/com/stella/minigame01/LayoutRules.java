package com.stella.minigame01;

public final class LayoutRules {
    private LayoutRules() {}

    public static float gameplayTop(float height) {
        return height * 0.235f;
    }

    public static float gameplayBottom(float height) {
        return height * 0.585f;
    }

    public static float micSafeTop(float height) {
        return height * 0.615f;
    }

    public static float rowY(float height, int row, int rows) {
        float top = gameplayTop(height);
        float bottom = gameplayBottom(height);
        if (rows <= 1) return (top + bottom) * 0.5f;
        return top + (bottom - top) * row / (rows - 1f);
    }
}
