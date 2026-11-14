package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.hud.HudStyle;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Counts the PvP items you carry: totems, gapples, pearls, splash potions, XP bottles, arrows. */
public class ItemCounterModule extends HudModule {
    private static final Item[][] GROUPS = {
            {Items.TOTEM_OF_UNDYING}, {Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE}, {Items.ENDER_PEARL},
            {Items.SPLASH_POTION}, {Items.EXPERIENCE_BOTTLE}, {Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW}};
    private final BoolSetting hideZero = add(new BoolSetting("Hide Empty", true));

    public ItemCounterModule() { super("Item Counter", "Count totems, pearls, pots", true, 60, 172); }

    private record Entry(ItemStack icon, int count) {}

    private List<Entry> entries(boolean editing) {
        List<Entry> out = new ArrayList<>();
        for (Item[] group : GROUPS) {
            int n = 0;
            if (mc.player != null) {
                var inv = mc.player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    for (Item it : group) if (s.is(it)) n += s.getCount();
                }
            }
            if (editing && n == 0) n = 4;
            if (n > 0 || !hideZero.on()) out.add(new Entry(new ItemStack(group[0]), n));
        }
        return out;
    }

    @Override public int width() { return 46; }
    @Override public int height() { return Math.max(18, entries(true).size() * 18); }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        int y = 0;
        for (Entry e : entries(editing)) {
            Draw.rect(g, 0, y, width(), 17, HudStyle.bg());
            g.renderItem(e.icon(), 1, y);
            String c = String.valueOf(e.count());
            HudStyle.text(g, c, width() - 3 - Draw.width(c), y + 5, HudStyle.text(y / 80f));
            y += 18;
        }
    }
}
