package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.hud.HudStyle;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cards for each active effect: icon, name + level, time left and a draining bar. */
public class PotionEffectsModule extends HudModule {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final int W = 132, ROW = 26, GAP = 3;

    public final BoolSetting hideVanilla = add(new BoolSetting("Hide Vanilla Icons", true));
    private final BoolSetting bars = add(new BoolSetting("Time Bars", true));
    private final BoolSetting blink = add(new BoolSetting("Blink When Ending", true));

    /** Longest duration seen per effect, so the bar has something to drain from. */
    private final Map<Holder<MobEffect>, Integer> maxSeen = new HashMap<>();

    private record Row(Holder<MobEffect> effect, String name, int ticks, boolean infinite, boolean good) {}

    public PotionEffectsModule() { super("Potion Effects", "Effect cards with icons and time bars", true, 500, 24); }

    private List<Row> rows(boolean editing) {
        List<Row> out = new ArrayList<>();
        if (mc.player != null) {
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                int amp = e.getAmplifier();
                String name = e.getEffect().value().getDisplayName().getString() + (amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1));
                out.add(new Row(e.getEffect(), name, e.getDuration(), e.isInfiniteDuration(), e.getEffect().value().isBeneficial()));
            }
        }
        if (out.isEmpty() && editing) {
            out.add(new Row(MobEffects.SPEED, "Speed II", 20 * 90, false, true));
            out.add(new Row(MobEffects.STRENGTH, "Strength", 20 * 45, false, true));
            out.add(new Row(MobEffects.POISON, "Poison", 20 * 6, false, false));
        }
        out.sort(Comparator.comparingInt((Row r) -> r.infinite() ? Integer.MAX_VALUE : r.ticks()).reversed());
        return out;
    }

    private static String time(Row r) {
        if (r.infinite()) return "∞";
        int s = r.ticks() / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    @Override public int width() { return W; }
    @Override public int height() { return Math.max(ROW, rows(true).size() * (ROW + GAP) - GAP); }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        long now = System.currentTimeMillis();
        int y = 0;
        for (Row r : rows(editing)) {
            int max = Math.max(maxSeen.merge(r.effect(), r.ticks(), Math::max), 1);
            boolean ending = !r.infinite() && r.ticks() < 200;
            float alpha = (blink.on() && ending) ? 0.55f + 0.45f * (float) Math.abs(Math.sin(now / 180.0)) : 1f;
            int a = (int) (alpha * 255);
            int accent = r.good() ? 0xFFFFFF : 0xFF5A5A;

            Draw.rect(g, 0, y, W, ROW, HudStyle.bg());
            Draw.rect(g, 0, y, 2, ROW, (a << 24) | accent);

            // vanilla effect icon
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(r.effect()), 5, y + 4, 18, 18);

            HudStyle.text(g, r.name(), 27, y + 4, (a << 24) | (HudStyle.text(y / 80f) & 0xFFFFFF));
            String t = time(r);
            HudStyle.text(g, t, W - 5 - Draw.font().width(t), y + 4, (a << 24) | (ending ? 0xFF8080 : (HudStyle.dim() & 0xFFFFFF)));

            if (bars.on()) {
                int bx = 27, bw = W - 27 - 5, by = y + 17;
                Draw.rect(g, bx, by, bw, 3, 0x40FFFFFF);
                float p = r.infinite() ? 1f : Math.min(1f, r.ticks() / (float) max);
                Draw.rect(g, bx, by, Math.max(1, (int) (bw * p)), 3, (a << 24) | accent);
            }
            y += ROW + GAP;
        }
        // forget effects that ended so a new one starts with a full bar
        if (mc.player != null && !editing)
            maxSeen.keySet().removeIf(h -> !mc.player.hasEffect(h));
    }
}
