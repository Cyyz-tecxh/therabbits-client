package com.therabbits.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Branded loading screen: charcoal background, pixel rabbit, "THE RABBITS" and a thin ice-blue progress bar.
 * Drawn over the vanilla screen with the same fade, so loading and fading behave exactly as before.
 * Only plain fills are used: textures and fonts are not loaded yet the first time this shows.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private boolean fadeIn;
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;

    private static final String[] RABBIT = {
            "..##....##..",
            ".####..####.",
            ".####..####.",
            ".####..####.",
            ".####..####.",
            "..###..###..",
            ".##########.",
            "############",
            "##..####..##",
            "############",
            "#####..#####",
            ".##########.",
            "..########..",
    };
    private static final java.util.Map<Character, String[]> FONT = java.util.Map.of(
            'T', new String[]{"#####", "..#..", "..#..", "..#..", "..#.."},
            'H', new String[]{"#...#", "#...#", "#####", "#...#", "#...#"},
            'E', new String[]{"#####", "#....", "####.", "#....", "#####"},
            'R', new String[]{"####.", "#...#", "####.", "#..#.", "#...#"},
            'A', new String[]{".###.", "#...#", "#####", "#...#", "#...#"},
            'B', new String[]{"####.", "#...#", "####.", "#...#", "####."},
            'I', new String[]{"#####", "..#..", "..#..", "..#..", "#####"},
            'S', new String[]{".####", "#....", ".###.", "....#", "####."});

    @Inject(method = "render", at = @At("TAIL"))
    private void therabbits$brand(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long now = System.nanoTime() / 1_000_000L;   // same clock as Util.getMillis()
        float out = fadeOutStart > -1L ? (now - fadeOutStart) / 1000f : -1f;
        float in = fadeInStart > -1L ? (now - fadeInStart) / 500f : -1f;
        float a;
        if (out >= 1f) a = 1f - Mth.clamp(out - 1f, 0f, 1f);
        else if (fadeIn) a = Mth.clamp(in, 0f, 1f);
        else a = 1f;
        if (a <= 0.01f) return;

        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, alpha(0x101419, a));

        int px = Math.max(2, h / 90);
        int rw = RABBIT[0].length() * px, rh = RABBIT.length * px;
        int cx = w / 2, top = h / 2 - rh - px * 4;
        draw(g, RABBIT, cx - rw / 2, top, px, alpha(0xF4F7FA, a));

        String word = "THE RABBITS";
        int lp = Math.max(1, px / 2), gap = lp;
        int tw = 0;
        for (char c : word.toCharArray()) tw += (c == ' ' ? 3 : 5) * lp + gap;
        int tx = cx - tw / 2, ty = h / 2 + px;
        for (char c : word.toCharArray()) {
            if (c != ' ') draw(g, FONT.get(c), tx, ty, lp, alpha(0xF4F7FA, a));
            tx += (c == ' ' ? 3 : 5) * lp + gap;
        }

        // progress bar
        int bw = Math.min(220, w / 3), bh = Math.max(2, px / 2), bx = cx - bw / 2, by = ty + 5 * lp + px * 5;
        g.fill(bx, by, bx + bw, by + bh, alpha(0x2C3540, a));
        int fill = Math.round(bw * Mth.clamp(currentProgress, 0f, 1f));
        g.fill(bx, by, bx + fill, by + bh, alpha(0x8BDCFF, a));
    }

    private static void draw(GuiGraphics g, String[] rows, int x, int y, int s, int color) {
        for (int r = 0; r < rows.length; r++)
            for (int c = 0; c < rows[r].length(); c++)
                if (rows[r].charAt(c) == '#') g.fill(x + c * s, y + r * s, x + c * s + s, y + r * s + s, color);
    }

    private static int alpha(int rgb, float a) { return ((int) (a * 255) << 24) | (rgb & 0xFFFFFF); }
}
