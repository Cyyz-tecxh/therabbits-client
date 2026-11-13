package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.gui.Theme;
import com.therabbits.client.module.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Lists your active potion effects with time left. */
public class PotionEffectsModule extends HudModule {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    public PotionEffectsModule() { super("Potion Effects", "Shows active effects and time left", true, 560, 4); }

    private List<String[]> lines(boolean editing) {
        List<String[]> out = new ArrayList<>();
        if (mc.player != null) {
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                int amp = e.getAmplifier();
                String name = e.getEffect().value().getDisplayName().getString() + (amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1));
                out.add(new String[]{name, time(e)});
            }
        }
        if (out.isEmpty() && editing) {
            out.add(new String[]{"Speed II", "1:30"});
            out.add(new String[]{"Strength", "0:45"});
        }
        return out;
    }

    private static String time(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "**";
        int s = e.getDuration() / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    @Override public int width() { return 100; }
    @Override public int height() { return Math.max(12, lines(true).size() * 12); }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        int y = 0;
        for (String[] l : lines(editing)) {
            Draw.rect(g, 0, y, width(), 11, Theme.HUD_BG);
            Draw.textShadow(g, l[0], 3, y + 2, Theme.TEXT);
            Draw.textShadow(g, l[1], width() - 3 - Draw.font().width(l[1]), y + 2, Theme.TEXT_DIM);
            y += 12;
        }
    }
}
