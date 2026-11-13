package com.therabbits.showcase;

import com.therabbits.client.gui.HudEditorScreen;
import com.therabbits.client.gui.ModMenuScreen;
import com.therabbits.client.gui.RabbitsTitleScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

/** Opens each screen and saves numbered screenshots for the showcase video. */
public class ShowcaseTest implements FabricClientGameTest {
    private int shot = 0;

    private void frames(ClientGameTestContext ctx, String scene, int count, long msPerFrame) {
        for (int i = 0; i < count; i++) {
            ctx.waitTick();
            sleep(msPerFrame);
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

    @Override
    public void runTest(ClientGameTestContext ctx) {
        // 1. Title screen
        ctx.runOnClient(mc -> mc.setScreen(new RabbitsTitleScreen()));
        frames(ctx, "a_title", 45, 40);
        int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
        // sweep the cursor down the buttons
        for (int i = 0; i < 60; i++) {
            double y = h * 0.33 + (h * 0.40) * i / 60.0;
            cursor(ctx, w * 0.62 + 40, y);
            ctx.waitTick();
            sleep(40);
            ctx.takeScreenshot(String.format("a_title_%03d", shot++));
        }

        // 2. Mod menu over the title background
        ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(new RabbitsTitleScreen())));
        cursor(ctx, w / 2.0, h / 2.0);
        frames(ctx, "b_menu", 20, 40);

        // 3. In a world with the HUD
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> mc.setScreen(null));
            frames(ctx, "c_world", 30, 40);

            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));
            frames(ctx, "d_worldmenu", 25, 40);

            ctx.runOnClient(mc -> mc.setScreen(new HudEditorScreen(null)));
            frames(ctx, "e_editor", 25, 40);
            ctx.runOnClient(mc -> mc.setScreen(null));
            frames(ctx, "f_world", 10, 40);
        }
    }
}
