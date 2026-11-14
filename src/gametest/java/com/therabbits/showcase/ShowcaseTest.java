package com.therabbits.showcase;

import com.therabbits.client.ModuleManager;
import com.therabbits.client.RabbitsClient;
import com.therabbits.client.gui.HudEditorScreen;
import com.therabbits.client.gui.ModMenuScreen;
import com.therabbits.client.gui.RabbitsTitleScreen;
import com.therabbits.client.module.combat.CrosshairModule;
import com.therabbits.client.module.hud.ClockModule;
import com.therabbits.client.module.hud.CoordinatesModule;
import com.therabbits.client.module.hud.DirectionModule;
import com.therabbits.client.module.hud.FpsModule;
import com.therabbits.client.module.hud.HudStyleModule;
import com.therabbits.client.module.visual.FullbrightModule;
import com.therabbits.client.module.visual.MenuBackgroundModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Feature showcase. Screenshot names start with a scene tag; the video script turns tags into captions.
 */
public class ShowcaseTest implements FabricClientGameTest {
    private int shot = 0;

    private void frames(ClientGameTestContext ctx, String scene, int count) {
        for (int i = 0; i < count; i++) {
            ctx.waitTick();
            sleep(35);
            ctx.takeScreenshot(String.format("%s_%04d", scene, shot++));
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static void cursor(ClientGameTestContext ctx, double guiX, double guiY) {
        double scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
        ctx.getInput().setCursorPos(guiX * scale, guiY * scale);
    }

    private static void click(ClientGameTestContext ctx, double guiX, double guiY) {
        cursor(ctx, guiX, guiY);
        ctx.waitTick();
        ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private void bigWindow(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 720);
        ctx.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
        });
        ctx.waitTicks(5);
    }

    /** Spawns a frozen zombie 3 blocks in front of the player. */
    private static Mob zombieAhead(ServerPlayer p, boolean totem) {
        ServerLevel level = (ServerLevel) p.level();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 pos = p.position().add(flat.scale(3.2));
        Mob z = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        z.setPos(pos.x, p.getY(), pos.z);
        z.setYRot(p.getYRot() + 180);
        z.setYHeadRot(p.getYRot() + 180);
        z.setNoAi(true);
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        if (totem) z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        level.addFreshEntity(z);
        return z;
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(3);
            mc.options.simulationDistance().set(5);
        });

        // ================= In-game features (world first: it loads faster in a small window) =================
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
                p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                p.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 300, 1));
                p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 20 * 240, 0));
                p.setXRot(0);
                server.overworld().setDayTime(1000);
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
            int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
            int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());

            // 1. HUD + keystrokes
            frames(ctx, "c01hud", 8);
            ctx.getInput().holdKey(o -> o.keyUp);
            frames(ctx, "c01hud", 12);
            ctx.getInput().releaseKey(o -> o.keyUp);
            ctx.getInput().holdKey(o -> o.keyLeft);
            frames(ctx, "c01hud", 6);
            ctx.getInput().releaseKey(o -> o.keyLeft);
            ctx.getInput().holdKey(o -> o.keyJump);
            frames(ctx, "c01hud", 5);
            ctx.getInput().releaseKey(o -> o.keyJump);
            frames(ctx, "c01hud", 5);

            // 1b. HUD style: colors and size
            ctx.runOnClient(mc -> {
                HudStyleModule st = ModuleManager.get(HudStyleModule.class);
                st.textColor.set(8);   // Aqua
                st.textSize.set(1.15);
            });
            frames(ctx, "c10style", 14);
            ctx.runOnClient(mc -> {
                ModuleManager.get(HudStyleModule.class).textColor.set(12);  // Chroma
                ModuleManager.get(FpsModule.class).textColor.set(4);        // FPS: Red (index 3 + Global)
            });
            frames(ctx, "c10style", 20);
            ctx.runOnClient(mc -> {
                HudStyleModule st = ModuleManager.get(HudStyleModule.class);
                st.textColor.set(0); st.textSize.set(1.0);
                ModuleManager.get(FpsModule.class).textColor.set(0);
            });
            // style settings in the mod menu (HUD tab, first row = HUD Style)
            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));
            ctx.waitTicks(3);
            {
                int mw = Math.min(480, w - 10), mh = Math.min(300, h - 10);
                click(ctx, w / 2 - mw / 2 + 120 + 40, h / 2 - mh / 2 + 18);
            }
            cursor(ctx, w / 2.0 + 200, h / 2.0 + 130);
            frames(ctx, "c11stylemenu", 18);
            ctx.runOnClient(mc -> mc.setScreen(null));

            // 2. Crosshair styles
            for (int s = 0; s <= 2; s++) {
                final double style = s;
                ctx.runOnClient(mc -> ModuleManager.get(CrosshairModule.class).style.set(style));
                frames(ctx, "c02cross", 10);
            }
            ctx.runOnClient(mc -> ModuleManager.get(CrosshairModule.class).style.set(0.0));

            // 3. Zoom
            ctx.getInput().holdKey(o -> RabbitsClient.zoomKey);
            frames(ctx, "c03zoom", 16);
            ctx.getInput().releaseKey(o -> RabbitsClient.zoomKey);
            frames(ctx, "c03zoom", 6);

            // 4. Reach: hit a zombie standing 3 blocks away
            sp.getServer().runOnServer(server -> zombieAhead(server.getPlayerList().getPlayers().get(0), false));
            frames(ctx, "c04reach", 6);
            for (int i = 0; i < 3; i++) {
                ctx.getInput().pressKey(o -> o.keyAttack);
                frames(ctx, "c04reach", 8);
            }

            // 5. Totem pops: a zombie holding a totem takes lethal damage twice
            sp.getServer().runOnServer(server -> {
                server.overworld().getEntities(EntityType.ZOMBIE, z -> true).forEach(z -> z.discard());
                Mob z = zombieAhead(server.getPlayerList().getPlayers().get(0), true);
                z.setCustomName(net.minecraft.network.chat.Component.literal("Rival"));
            });
            frames(ctx, "c05totem", 6);
            for (int i = 0; i < 2; i++) {
                sp.getServer().runOnServer(server -> server.overworld().getEntities(EntityType.ZOMBIE, z -> true).forEach(z -> {
                    z.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                    z.hurtServer(server.overworld(), server.overworld().damageSources().generic(), 1000f);
                }));
                frames(ctx, "c05totem", 14);
            }
            sp.getServer().runOnServer(server -> server.overworld().getEntities(EntityType.ZOMBIE, z -> true).forEach(z -> z.discard()));

            // 6. Freelook
            ctx.getInput().holdKey(o -> RabbitsClient.freelookKey);
            ctx.waitTicks(2);
            for (int i = 0; i < 30; i++) {
                ctx.getInput().moveCursor(20, i < 15 ? -3 : 3);
                ctx.waitTick();
                sleep(35);
                ctx.takeScreenshot(String.format("c06free_%04d", shot++));
            }
            ctx.getInput().releaseKey(o -> RabbitsClient.freelookKey);
            frames(ctx, "c06free", 4);

            // 7. Fullbright at night
            sp.getServer().runOnServer(server -> server.overworld().setDayTime(18000));
            frames(ctx, "c07bright", 12);
            ctx.runOnClient(mc -> ModuleManager.get(FullbrightModule.class).setEnabled(true));
            frames(ctx, "c07bright", 14);
            ctx.runOnClient(mc -> ModuleManager.get(FullbrightModule.class).setEnabled(false));
            sp.getServer().runOnServer(server -> server.overworld().setDayTime(1000));

            // 8. Mod menu: click through each category
            ctx.runOnClient(mc -> mc.setScreen(new ModMenuScreen(null)));
            frames(ctx, "c08menu", 10);
            int mw = Math.min(480, w - 10), mh = Math.min(300, h - 10);
            int left = w / 2 - mw / 2, top = h / 2 - mh / 2;
            for (int c = 1; c < 4; c++) {
                click(ctx, left + 40, top + 86 + c * 22 + 9);
                cursor(ctx, w / 2.0 + 150, h / 2.0 + 120);
                frames(ctx, "c08menu", 14);
            }

            // 9. HUD editor
            cursor(ctx, w / 2.0, h / 2.0);
            ctx.runOnClient(mc -> mc.setScreen(new HudEditorScreen(null)));
            frames(ctx, "c09editor", 18);
            ctx.runOnClient(mc -> mc.setScreen(null));
            frames(ctx, "c09editor", 4);
        }

        // ================= Start menu =================
        bigWindow(ctx);
        int w = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth());
        int h = ctx.computeOnClient(mc -> mc.getWindow().getGuiScaledHeight());
        cursor(ctx, w - 5, h - 30);
        ctx.runOnClient(mc -> {
            ModuleManager.get(MenuBackgroundModule.class).style.set(0.0);
            mc.setScreen(new RabbitsTitleScreen());
        });
        frames(ctx, "a00title", 36);
        int panelX = (int) (w * 0.64) - 119 + 20;
        for (int i = 0; i < 40; i++) {
            cursor(ctx, panelX + 140, h * 0.36 + (h * 0.45) * i / 40.0);
            ctx.waitTick();
            sleep(35);
            ctx.takeScreenshot(String.format("a00title_%04d", shot++));
        }
        for (int s : new int[]{3, 10, 12, 14}) {
            final double style = s;
            ctx.runOnClient(mc -> ModuleManager.get(MenuBackgroundModule.class).style.set(style));
            frames(ctx, "a01bg", 12);
        }
        for (int i = 0; i < 4; i++) {
            ctx.runOnClient(mc -> com.therabbits.client.skin.SkinChanger.next());
            frames(ctx, "a02skin", 10);
        }
        ctx.runOnClient(mc -> {
            com.therabbits.client.skin.SkinChanger.reset();
            ModuleManager.get(MenuBackgroundModule.class).style.set(0.0);
        });
    }
}
