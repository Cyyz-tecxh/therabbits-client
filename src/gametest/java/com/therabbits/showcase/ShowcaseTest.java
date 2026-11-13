package com.therabbits.showcase;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.RabbitsClient;
import com.therabbits.client.gui.HudEditorScreen;
import com.therabbits.client.gui.ModMenuScreen;
import com.therabbits.client.gui.RabbitsTitleScreen;
import com.therabbits.client.module.hud.ClockModule;
import com.therabbits.client.module.hud.CoordinatesModule;
import com.therabbits.client.module.hud.DirectionModule;
import com.therabbits.client.module.visual.FullbrightModule;
import com.therabbits.client.module.visual.MenuBackgroundModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

    private void style(ClientGameTestContext ctx, int s) {
        ctx.runOnClient(mc -> ModuleManager.get(MenuBackgroundModule.class).style.set((double) s));
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(5);
            mc.options.simulationDistance().set(5);
        });

        // ---- Start menu first ----
        bigWindow(ctx);
        int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());

        cursor(ctx, w - 5, h - 30);
        style(ctx, 0);
        ctx.runOnClient(mc -> mc.setScreen(new RabbitsTitleScreen()));
        frames(ctx, "a_title", 40);
        int panelX = (int) (w * 0.64) - 119 + 20;
        for (int i = 0; i < 50; i++) {
            double y = h * 0.36 + (h * 0.45) * i / 50.0;
            cursor(ctx, panelX + 140, y);
            ctx.waitTick();
            sleep(35);
            ctx.takeScreenshot(String.format("a_title_%03d", shot++));
        }
        // a tour of the backgrounds: some images, then the animated ones
        int[] tour = {1, 3, 4, 7, 10, 12, 14,
                MenuBackgroundModule.IMAGES.length + 1, MenuBackgroundModule.IMAGES.length + 3};
        cursor(ctx, 120, 43);
        for (int s : tour) {
            style(ctx, s);
            frames(ctx, "b_bg", 14);
        }
        style(ctx, 0);

        // skin changer: step through a few skins
        for (int i = 0; i < 5; i++) {
            ctx.runOnClient(mc -> com.therabbits.client.skin.SkinChanger.next());
            frames(ctx, "bb_skin", 12);
        }
        ctx.runOnClient(mc -> com.therabbits.client.skin.SkinChanger.reset());

        cursor(ctx, w / 2.0 + 150, h / 2.0 + 60);
        ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(new RabbitsTitleScreen())));
        frames(ctx, "c_menu", 20);

        // ---- World ----
        ctx.getInput().resizeWindow(854, 480);
        ctx.waitTicks(3);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 95, 1));
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 45, 0));
            });
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> {
                ModuleManager.get(DirectionModule.class).setEnabled(true);
                ModuleManager.get(CoordinatesModule.class).setEnabled(true);
                ModuleManager.get(ClockModule.class).setEnabled(true);
                mc.setScreen(null);
            });
            bigWindow(ctx);
            sp.getClientWorld().waitForChunksRender();

            frames(ctx, "d_world", 8);
            ctx.getInput().holdKey(o -> o.keyUp);
            frames(ctx, "d_world", 14);
            ctx.getInput().releaseKey(o -> o.keyUp);
            ctx.getInput().holdKey(o -> o.keyJump);
            frames(ctx, "d_world", 6);
            ctx.getInput().releaseKey(o -> o.keyJump);
            frames(ctx, "d_world", 4);

            // Freelook: hold Left Alt and swing the camera around the player
            ctx.getInput().holdKey(o -> RabbitsClient.freelookKey);
            ctx.waitTicks(2);
            for (int i = 0; i < 28; i++) {
                ctx.getInput().moveCursor(20, i < 14 ? -3 : 3);
                ctx.waitTick();
                sleep(35);
                ctx.takeScreenshot(String.format("e_freelook_%03d", shot++));
            }
            ctx.getInput().releaseKey(o -> RabbitsClient.freelookKey);
            frames(ctx, "e_freelook", 4);

            // Night, then Fullbright on
            sp.getServer().runOnServer(server -> server.overworld().setDayTime(18000));
            frames(ctx, "f_night", 12);
            ctx.runOnClient(mc -> ModuleManager.get(FullbrightModule.class).setEnabled(true));
            frames(ctx, "f_night", 14);
            ctx.runOnClient(mc -> ModuleManager.get(FullbrightModule.class).setEnabled(false));
            sp.getServer().runOnServer(server -> server.overworld().setDayTime(6000));

            int ww = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
            int hh = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
            cursor(ctx, ww / 2.0 + 150, hh / 2.0 + 60);
            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));
            frames(ctx, "g_worldmenu", 18);

            cursor(ctx, ww / 2.0, hh / 2.0);
            ctx.runOnClient(mc -> mc.setScreen(new HudEditorScreen(null)));
            frames(ctx, "h_editor", 18);
            ctx.runOnClient(mc -> mc.setScreen(null));
            frames(ctx, "i_world", 6);
        }
    }
}
