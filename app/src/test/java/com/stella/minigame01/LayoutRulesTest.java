package com.stella.minigame01;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class LayoutRulesTest {
    @Test
    public void lastGameplayHitboxStaysAboveMicSafeZone() {
        float width = 720f;
        float height = 1600f;
        float radius = Math.min(width, height) * 0.057f;
        float hitRadius = radius * 1.25f;
        float lastRowCenter = LayoutRules.rowY(height, 4, 5);

        assertTrue(lastRowCenter + hitRadius < LayoutRules.micSafeTop(height));
    }
}
