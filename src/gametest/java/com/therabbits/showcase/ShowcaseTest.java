package com.therabbits.showcase;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.gui.HudEditorScreen;
import com.therabbits.client.gui.ModMenuScreen;
import com.therabbits.client.gui.RabbitsTitleScreen;
import com.therabbits.client.module.hud.CoordinatesModule;
import com.therabbits.client.module.hud.DirectionModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Opens each screen and saves numbered screenshots for the showcase video. */
public class ShowcaseTest implements FabricClientGameTest {
    private int shot = 0;

    private void frames(ClientGameTestContext ctx, String scene, int count) {
        for (int i = 0; i < count; i++) {
            ctx.waitTick();
            sleep(35);
            ctx.takeScreenshot(String.format("%s_%03d", scene, shot++));
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    /** GUI coordinates -> window pixels. */
    private static void cursor(ClientGameTestContext ctx, double guiX, double guiY) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
    }

    private void bigWindow(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
        });
        ctx.waitTicks(5);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        // World first, while the window is small (world loading is slow on CI's software graphics).
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
            });
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> {
                ModuleManager.get(DirectionModule.class).setEnabled(true);
                ModuleManager.get(CoordinatesModule.class).setEnabled(true);
                mc.setScreen(null);
            });
            bigWindow(ctx);
            sp.getClientWorld().waitForChunksRender();
            int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
            int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
            frames(ctx, "c_world", 12);
            ctx.getInput().holdKey(o -> o.keyUp);
            frames(ctx, "c_world", 20);
            ctx.getInput().releaseKey(o -> o.keyUp);
            ctx.getInput().holdKey(o -> o.keyLeft);
            frames(ctx, "c_world", 10);
            ctx.getInput().releaseKey(o -> o.keyLeft);
            ctx.getInput().holdKey(o -> o.keyJump);
            frames(ctx, "c_world", 6);
            ctx.getInput().releaseKey(o -> o.keyJump);
            frames(ctx, "c_world", 8);

            cursor(ctx, w / 2.0 + 150, h / 2.0 + 60);
            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));
            frames(ctx, "d_worldmenu", 25);

            cursor(ctx, w / 2.0, h / 2.0);
            ctx.runOnClient(mc -> mc.setScreen(new HudEditorScreen(null)));
            frames(ctx, "e_editor", 25);
            ctx.runOnClient(mc -> mc.setScreen(null));
            frames(ctx, "f_world", 8);
        }

        bigWindow(ctx);
        int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());

        // Title screen intro, then sweep the cursor down the buttons
        cursor(ctx, w - 5, h - 30);
        ctx.runOnClient(mc -> mc.setScreen(new RabbitsTitleScreen()));
        frames(ctx, "a_title", 40);
        int panelX = (int) (w * 0.62) - 100 + 40;
        for (int i = 0; i < 70; i++) {
            double y = h * 0.30 + (h * 0.42) * i / 70.0;
            cursor(ctx, panelX + 120, y);
            ctx.waitTick();
            sleep(35);
            ctx.takeScreenshot(String.format("a_title_%03d", shot++));
        }

        // Mod menu over the title background
        cursor(ctx, w / 2.0 + 150, h / 2.0 + 60);
        ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(new RabbitsTitleScreen())));
        frames(ctx, "b_menu", 30);
    }
}
