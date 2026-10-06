package com.stella.minigame01;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LayoutRulesTest {
    @Test
    public void lastGameplayRowStaysAboveMicSafeZone() {
        float height = 1600f;
        assertTrue(LayoutRules.gameplayBottom(height) < LayoutRules.micSafeTop(height));
        assertTrue(LayoutRules.rowY(height, 4, 5) < LayoutRules.micSafeTop(height));
    }
}
