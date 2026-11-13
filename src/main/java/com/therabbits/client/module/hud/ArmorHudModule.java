package com.therabbits.client.module.hud;

import com.therabbits.client.gui.Draw;
import com.therabbits.client.gui.Theme;
import com.therabbits.client.module.HudModule;
import com.therabbits.client.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ArmorHudModule extends HudModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ItemStack[] PREVIEW = {
            new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};

    private final BoolSetting durability = add(new BoolSetting("Durability Text", true));
    private final BoolSetting mainHand = add(new BoolSetting("Held Item", true));

    public ArmorHudModule() { super("Armor HUD", "Shows your armor and its durability", true, 4, 160); }

    @Override public int width() { return durability.on() ? 50 : 18; }
    @Override public int height() { return 18 * (mainHand.on() ? 5 : 4); }

    @Override
    public void render(GuiGraphics g, boolean editing) {
        int y = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack stack = mc.player == null || editing && mc.player.getItemBySlot(SLOTS[i]).isEmpty()
                    ? PREVIEW[i] : mc.player.getItemBySlot(SLOTS[i]);
            item(g, stack, y);
            y += 18;
        }
        if (mainHand.on() && mc.player != null) item(g, mc.player.getMainHandItem(), y);
    }

    private void item(GuiGraphics g, ItemStack stack, int y) {
        if (stack.isEmpty()) return;
        g.renderItem(stack, 0, y);
        g.renderItemDecorations(Draw.font(), stack, 0, y);
        if (durability.on() && stack.isDamageableItem()) {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            Draw.textShadow(g, String.valueOf(left), 20, y + 5, Theme.TEXT);
        }
    }
}
