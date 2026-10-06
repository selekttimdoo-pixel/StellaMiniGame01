package com.stella.minigame01;

public final class SpriteLayout {
    private SpriteLayout() {}

    public static int cellLeft(int sheetWidth, int columns, int column) {
        return (sheetWidth / columns) * column;
    }

    public static int rowTop(int row) {
        switch (row) {
            case 0: return 125;
            case 1: return 232;
            default: return 340;
        }
    }

    public static int cropSize() {
        return 60;
    }
}
