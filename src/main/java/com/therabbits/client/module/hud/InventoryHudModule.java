package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.hud.HudStyle;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shows your main inventory (the 27 slots above the hotbar) on the HUD. */
public class InventoryHudModule extends HudModule {
    private final BoolSetting slotOutlines = add(new BoolSetting("Slot Outlines", true));

    public InventoryHudModule() { super("Inventory HUD", "Inventory on screen", false, 220, 290); }

    @Override public int width() { return 9 * 18 + 4; }
    @Override public int height() { return 3 * 18 + 4; }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        Draw.rect(g, 0, 0, width(), height(), HudStyle.bg() == 0 ? 0x40000000 : HudStyle.bg());
        for (int i = 0; i < 27; i++) {
            int x = 2 + (i % 9) * 18, y = 2 + (i / 9) * 18;
            if (slotOutlines.on()) Draw.outline(g, x, y, 18, 18, 0x30FFFFFF);
            ItemStack s = mc.player != null ? mc.player.getInventory().getItem(9 + i)
                    : (editing && i % 4 == 0 ? new ItemStack(Items.COBBLESTONE, 64) : ItemStack.EMPTY);
            if (editing && mc.player == null && s.isEmpty()) continue;
            if (!s.isEmpty()) {
                g.renderItem(s, x + 1, y + 1);
                g.renderItemDecorations(Draw.font(), s, x + 1, y + 1);
            }
        }
    }
}
