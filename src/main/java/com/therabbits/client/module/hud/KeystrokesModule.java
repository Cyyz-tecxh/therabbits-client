package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.gui.Theme;
import com.therabbits.client.hud.CpsTracker;
import com.therabbits.client.hud.HudStyle;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

public class KeystrokesModule extends HudModule {
    private static final int K = 22, G = 2;
    private final BoolSetting mouse = add(new BoolSetting("Mouse Buttons", true));
    private final BoolSetting cps = add(new BoolSetting("Show CPS", true));
    private final BoolSetting space = add(new BoolSetting("Space Bar", true));

    public KeystrokesModule() { super("Keystrokes", "Shows WASD, mouse clicks and CPS", true, 4, 80); }

    @Override public int width() { return K * 3 + G * 2; }

    @Override
    public int height() {
        int h = K * 2 + G;
        if (mouse.on()) h += K + G;
        if (space.on()) h += 10 + G;
        return h;
    }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        var o = mc.options;
        key(g, K + G, 0, K, K, "W", o.keyUp);
        key(g, 0, K + G, K, K, "A", o.keyLeft);
        key(g, K + G, K + G, K, K, "S", o.keyDown);
        key(g, (K + G) * 2, K + G, K, K, "D", o.keyRight);
        int y = (K + G) * 2;
        if (mouse.on()) {
            int half = (width() - G) / 2;
            key(g, 0, y, half, K, cps.on() ? CpsTracker.left() + " CPS" : "LMB", o.keyAttack);
            key(g, half + G, y, width() - half - G, K, cps.on() ? CpsTracker.right() + " CPS" : "RMB", o.keyUse);
            y += K + G;
        }
        if (space.on()) {
            boolean down = o.keyJump.isDown();
            Draw.rect(g, 0, y, width(), 10, down ? HudStyle.text() : HudStyle.bg());
            Draw.rect(g, width() / 2 - 10, y + 4, 20, 1, down ? 0xFF000000 : HudStyle.text());
        }
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, String label, KeyMapping key) {
        boolean down = key.isDown();
        Draw.rect(g, x, y, w, h, down ? HudStyle.text() : HudStyle.bg());
        if (!down && (HudStyle.bg() >>> 24) < 20) Draw.outline(g, x, y, w, h, 0x50FFFFFF);
        int tx = x + w / 2 - Draw.font().width(label) / 2;
        if (down) Draw.text(g, label, tx, y + h / 2 - 4, 0xFF000000);
        else HudStyle.text(g, label, tx, y + h / 2 - 4, HudStyle.text());
    }
}
