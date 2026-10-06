package com.stella.minigame01;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SpriteLayoutTest {
    @Test
    public void mapsGeneratedSpriteSheetCells() {
        assertEquals(360, SpriteLayout.cellLeft(420, 7, 6));
        assertEquals(125, SpriteLayout.rowTop(0));
        assertEquals(232, SpriteLayout.rowTop(1));
        assertEquals(340, SpriteLayout.rowTop(2));
        assertEquals(60, SpriteLayout.cropSize());
    }
}
