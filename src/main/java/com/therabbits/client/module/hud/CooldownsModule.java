package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.hud.HudStyle;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Shows item cooldowns (pearl, chorus, shield, wind charge...) and your attack cooldown as bars. */
public class CooldownsModule extends HudModule {
    private static final ItemStack[] TRACKED = {
            new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.CHORUS_FRUIT), new ItemStack(Items.SHIELD),
            new ItemStack(Items.WIND_CHARGE), new ItemStack(Items.GOAT_HORN), new ItemStack(Items.TRIDENT)};
    private final BoolSetting attack = add(new BoolSetting("Attack Cooldown", true));

    public CooldownsModule() { super("Cooldowns", "Pearl, shield, wind charge and attack cooldowns", true, 4, 358); }

    private record Row(ItemStack icon, float progress) {}

    private List<Row> rows(boolean editing) {
        List<Row> out = new ArrayList<>();
        if (mc.player != null) {
            var cd = mc.player.getCooldowns();
            for (ItemStack s : TRACKED) {
                float p = cd.getCooldownPercent(s, 0f);
                if (p > 0) out.add(new Row(s, p));
            }
            if (attack.on()) {
                float a = mc.player.getAttackStrengthScale(0f);
                if (a < 1f) out.add(new Row(mc.player.getMainHandItem().isEmpty() ? new ItemStack(Items.IRON_SWORD) : mc.player.getMainHandItem(), 1f - a));
            }
        }
        if (out.isEmpty() && editing) { out.add(new Row(TRACKED[0], 0.6f)); out.add(new Row(TRACKED[2], 0.3f)); }
        return out;
    }

    @Override public int width() { return 70; }
    @Override public int height() { return Math.max(18, rows(true).size() * 18); }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        int y = 0;
        for (Row r : rows(editing)) {
            if ((HudStyle.bg() >>> 24) > 0) Draw.rect(g, 0, y, width(), 17, HudStyle.bg());
            g.renderItem(r.icon(), 1, y);
            int bx = 20, bw = width() - 24;
            Draw.rect(g, bx, y + 7, bw, 3, 0x40FFFFFF);
            Draw.rect(g, bx, y + 7, Math.max(1, (int) (bw * r.progress())), 3, HudStyle.text(y / 60f));
            y += 18;
        }
    }
}
